package net.rrm.ehour.backup.service.restore;

import net.rrm.ehour.backup.domain.ParseSession;
import net.rrm.ehour.backup.domain.ParserUtil;
import net.rrm.ehour.backup.service.backup.BackupConfig;
import net.rrm.ehour.backup.service.backup.BackupEntityType;
import net.rrm.ehour.backup.service.restore.structure.FieldDefinition;
import net.rrm.ehour.backup.service.restore.structure.FieldMapFactory;
import net.rrm.ehour.domain.DomainObject;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Not thread safe
 *
 * @author thies (Thies Edeling - thies@te-con.nl)
 *         Created on: Nov 16, 2010 - 11:18:59 PM
 */
// TODO rename to EntityParser
public class DomainObjectParser {
    private DomainObjectParserDao parserDao;

    private XMLEventReader reader;

    private static final Logger LOG = Logger.getLogger(DomainObjectParser.class);

    private static final Map<Class<?>, TypeTransformer<?>> transformerMap = new HashMap<>();
    private ParseSession status;

    private PrimaryKeyCache keyCache;
    private final BackupConfig backupConfig;

    static {
        transformerMap.put(Integer.class, new IntegerTransformer());
        transformerMap.put(Float.class, new FloatTransformer());
        transformerMap.put(BigDecimal.class, new BigDecimalTransformer());
        transformerMap.put(Date.class, new DateTransformer());
        transformerMap.put(Boolean.class, new BooleanTransformer());
    }

    public DomainObjectParser(XMLEventReader reader, DomainObjectParserDao parserDao, PrimaryKeyCache keyCache, BackupConfig backupConfig) {
        this.parserDao = parserDao;
        this.reader = reader;
        this.keyCache = keyCache;
        this.backupConfig = backupConfig;
    }

    public <PK extends Serializable, T extends DomainObject<PK, ?>>  List<T> parse(Class<T> clazz, JoinTables joinTables, ParseSession status) throws IllegalAccessException, InstantiationException, XMLStreamException {
        Map<String, FieldDefinition> fieldMap = FieldMapFactory.buildFieldMapForDomainObject(clazz);
        this.status = status;

        return parseDomainObjects(clazz, fieldMap, joinTables, status);
    }

    /**
     * Parse domain object with reader pointing on the table name tag
     */
    private <PK extends Serializable, T extends DomainObject<PK, ?>>  List<T> parseDomainObjects(Class<T> clazz, Map<String, FieldDefinition> fieldMap, JoinTables joinTables, ParseSession status) throws XMLStreamException, IllegalAccessException, InstantiationException {
        List<T> domainObjects = new ArrayList<>();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextTag();

            if (event.isStartElement()) {
                T domainObject = parseAndPersistDomainObject(clazz, fieldMap, joinTables);

                domainObjects.add(domainObject);

                BackupEntityType backupEntityType = backupConfig.entityForClass(clazz);
                status.addInsertion(backupEntityType);
            } else if (event.isEndElement()) {
                break;
            }
        }

        return domainObjects;
    }

    @SuppressWarnings("unchecked")
    private <PK extends Serializable, T extends DomainObject<PK, ?>> T parseAndPersistDomainObject(Class<T> clazz, Map<String, FieldDefinition> fieldMap, JoinTables joinTables) throws XMLStreamException, IllegalAccessException, InstantiationException {
        T targetObject = clazz.newInstance();

        Map<Class<?>, Object> embeddables = new HashMap<>();

        while (reader.hasNext()) {
            XMLEvent event = reader.nextTag();

            if (event.isEndElement()) {
                break;
            }

            StartElement startElement = event.asStartElement();
            String dbField = startElement.getName().getLocalPart();
            FieldDefinition fieldDefinition = fieldMap.get(dbField.toLowerCase());

            Field targetField = fieldDefinition.getField();
            Class<? extends Serializable> type = (Class<? extends Serializable>) targetField.getType();

            String columnValue = ParserUtil.parseNextEventAsCharacters(reader);
            Object parsedColumnValue = parseColumn(type, columnValue, fieldDefinition.isIgnorable());

            if (parsedColumnValue != null) {
                fieldDefinition.process(targetObject, embeddables, parsedColumnValue);
            }
        }

        boolean hasCompositeKey = setEmbeddablesInDomainObject(fieldMap, targetObject, embeddables);

        PK originalKey = targetObject.getPK();

        resetId(fieldMap, targetObject);

        Serializable primaryKey = parserDao.persist(targetObject);

        if (!hasCompositeKey) {
            keyCache.putKey(targetObject.getClass(), originalKey, primaryKey);
        }

        return targetObject;
    }


    private <T> boolean setEmbeddablesInDomainObject(Map<String, FieldDefinition> fieldMap, T domainObject, Map<Class<?>, Object> embeddables)
            throws IllegalAccessException {
        boolean hasCompositeKey = false;

        for (FieldDefinition fieldDefinition : fieldMap.values()) {
            Field field = fieldDefinition.getField();
            Class<?> fieldType = field.getType();

            if (fieldType.isAnnotationPresent(Embeddable.class)) {
                field.set(domainObject, embeddables.get(fieldType));

                hasCompositeKey = true;
            }
        }

        return hasCompositeKey;
    }

    private <T> void resetId(Map<String, FieldDefinition> fieldMap, T domainObject) throws IllegalAccessException {
        for (FieldDefinition fieldDefinition : fieldMap.values()) {
            Field field = fieldDefinition.getField();

            // only reset generated value id's as they're re-generated. Composite key's should be left alone
            if (field.isAnnotationPresent(Id.class) && field.isAnnotationPresent(GeneratedValue.class)) {
                field.set(domainObject, null);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Serializable parseColumn(Class<? extends Serializable> columnType, String value, boolean canBeIgnored)
            throws IllegalAccessException, InstantiationException {
        Serializable parsedValue = null;

        if (columnType.isAnnotationPresent(Entity.class)) {
            Serializable castToFk = castToFkType(columnType, value);
            Serializable persistedKey = keyCache.getKey(columnType, castToFk);

            if (persistedKey != null) {
                parsedValue = parserDao.find(persistedKey, columnType);
            }

            if (parsedValue == null && !canBeIgnored) {
                status.addError(backupConfig.entityForClass(columnType), "ManyToOne relation not resolved");
            }
        } else if (columnType == String.class) {
            parsedValue = value;
        } else if (columnType.isEnum()) {
            parsedValue = Enum.valueOf((Class<Enum>) columnType, value);
        } else {
            if (transformerMap.containsKey(columnType)) {
                parsedValue = transformerMap.get(columnType).transform(value);
            } else {
                status.addError(backupConfig.entityForClass(columnType), "unknown type: " + columnType);
                LOG.error("no transformer for type " + columnType);
            }
        }
        return parsedValue;
    }

    @SuppressWarnings("unchecked")
    private Serializable castToFkType(Class<?> fkObjectType, String value) throws InstantiationException, IllegalAccessException {
        Field[] fields = fkObjectType.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Id.class)) {
                return parseColumn((Class<? extends Serializable>) field.getType(), value, false);
            }
        }

        return value;
    }

    PrimaryKeyCache getKeyCache() {
        return keyCache;
    }

    private interface TypeTransformer<T extends Serializable> {
        T transform(String value);
    }

    private static class IntegerTransformer implements TypeTransformer<Integer> {
        @Override
        public Integer transform(String value) {
            return StringUtils.isNotBlank(value) ? Integer.parseInt(value) : null;
        }
    }

    private static class BooleanTransformer implements TypeTransformer<Boolean> {
        @Override
        public Boolean transform(String value) {
            return "y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
        }
    }

    private static class FloatTransformer implements TypeTransformer<Float> {
        @Override
        public Float transform(String value) {
            return StringUtils.isNotBlank(value) ? Float.parseFloat(value) : null;
        }
    }

    private static class BigDecimalTransformer implements TypeTransformer<BigDecimal> {

        @Override
        public BigDecimal transform(String value) {
            return StringUtils.isNotBlank(value) ? new BigDecimal(value) : null;
        }
    }

    private static class DateTransformer implements TypeTransformer<Date> {
        @Override
        public Date transform(String value) {
            try {
                return new SimpleDateFormat("yyyy-MM-dd").parse(value);
            } catch (ParseException e) {
                LOG.error("Failed to parse date: " + value);
                return null;
            }
        }
    }
}

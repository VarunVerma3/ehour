package net.rrm.ehour.ui.admin.content.assignees;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeModel;

import net.rrm.ehour.domain.ProjectAssignment;
import net.rrm.ehour.domain.User;
import net.rrm.ehour.domain.UserDepartment;
import net.rrm.ehour.ui.admin.content.tree.AssigneeTreeNode;
import net.rrm.ehour.ui.admin.content.tree.ContentTree;
import net.rrm.ehour.ui.admin.content.tree.NodeType;
import net.rrm.ehour.ui.admin.content.tree.TreeFinder;
import net.rrm.ehour.ui.common.panel.AbstractAjaxPanel;
import net.rrm.ehour.user.service.UserService;

import org.apache.wicket.spring.injection.annot.SpringBean;

/**
 * Created on Feb 6, 2010 10:08:17 PM
 *
 * @author thies (www.te-con.nl)
 *
 */
public class AssigneesPanel extends AbstractAjaxPanel<Void>
{
	private static final long serialVersionUID = 2727952773526028264L;

	@SpringBean
	private UserService userService;

	private ContentTree tree;
	private static final TreeFinder<User> finder = new TreeFinder<User>(UserMatcher.getInstance());
	
	public AssigneesPanel(String id)
	{
		super(id);
	
		setOutputMarkupId(true);
		
		TreeModel model = createTreeModel();
		
		tree = new ContentTree("tree", model);
		tree.setRootLess(true);
		add(tree);
	}
	
	public void selectUsers(Collection<ProjectAssignment> assignments)
	{
		Set<Integer> ids = getUserIdsFromAssignments(assignments);
		
		TreeModel treeModel = tree.getModelObject();
		
		List<User> nodes = finder.findMatchingNodes(treeModel, ids);
	}
	
	private Set<Integer> getUserIdsFromAssignments(Collection<ProjectAssignment> assignments)
	{
		Set<Integer> userIds = new HashSet<Integer>();
		
		for (ProjectAssignment projectAssignment : assignments)
		{
			userIds.add(projectAssignment.getUser().getPK());
		}
		
		return userIds;
	}
		
	private TreeModel createTreeModel()
	{
		DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
		
		addNodesToRoot(rootNode);
		
		DefaultTreeModel model = new DefaultTreeModel(rootNode);
		return model;
	}

	private void addNodesToRoot(MutableTreeNode rootNode)
	{
		List<UserDepartment> departments = userService.getUserDepartments();
		
		for (UserDepartment department : departments)
		{
			AssigneeTreeNode<UserDepartment> departmentNode = new AssigneeTreeNode<UserDepartment>(department, NodeType.DEPARTMENT);
			
			rootNode.insert(departmentNode, 0);
			
			List<User> users = new ArrayList<User>(department.getUsers());
			Collections.sort(users);
			
			for (User user : users)
			{
				AssigneeTreeNode<User> userNode = new AssigneeTreeNode<User>(user, NodeType.DEPARTMENT);
				departmentNode.add(userNode);
			}
		}
	}
}

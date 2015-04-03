package org.opentdc.resources.opencrx;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jmi.reflect.DuplicateException;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.openmdx.base.exception.ServiceException;
import org.openmdx.base.naming.Path;
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.NotFoundException;

public class OpencrxServiceProvider implements ServiceProvider {
	
	public static final String XRI_ACTIVITY_SEGMENT = "xri://@openmdx*org.opencrx.kernel.activity1";
	public static final String XRI_ACCOUNT_SEGMENT = "xri://@openmdx*org.opencrx.kernel.account1";
	public static final short ACTIVITY_GROUP_TYPE_PROJECT = 40;
	public static final short ACCOUNT_ROLE_CUSTOMER = 100;
	public static final short ACTIVITY_CLASS_INCIDENT = 2;
	public static final short ICAL_TYPE_NA = 0;
	public static final short ICAL_CLASS_NA = 0;
	public static final short ICAL_TYPE_VEVENT = 1;

	private static PersistenceManagerFactory pmf = null;
	private static String providerName = null;
	private static String segmentName = null;
	private static org.opencrx.kernel.activity1.jmi1.Segment activitySegment = null;
	private static String url = null;
	private static String userName = null;
	private static String password = null;
	private static String mimeType = null;

	protected static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	// instance variables

	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) {
		logger.info("> OpencrxImpl()");

		if (url == null) {
			url = context.getInitParameter("backend.url");
		}
		if (userName == null) {
			userName = context.getInitParameter("backend.userName");
		}
		if (password == null) {
			password = context.getInitParameter("backend.password");
		}
		if (mimeType == null) {
			mimeType = context.getInitParameter("backend.mimeType");
		}
		if (providerName == null) {
			providerName = context.getInitParameter("backend.providerName");
		}
		if (segmentName == null) {
			segmentName = context.getInitParameter("backend.segmentName");
		}
		if (activitySegment == null) {
			activitySegment = getActivitySegment(getPersistenceManager());
		}

		logger.info("OpencrxImpl() initialized");
	}

	/******************************** resource *****************************************/
	/**
	 * List all resources.
	 * 
	 * @return a list of all resources.
	 */
	@Override
	public ArrayList<ResourceModel> listResources() {
		// TODO: implement listResources
		logger.info("listResources() -> " + countResources() + " resources");
		throw new org.opentdc.service.exception.NotImplementedException("listResources is not yet implemented");
	}

	/**
	 * Create a new Resource.
	 * 
	 * @param resource
	 * @return the newly created resource (can be different than resource param)
	 * @throws DuplicateException
	 *             if a resource with the same ID already exists.
	 */
	@Override
	public ResourceModel createResource(ResourceModel resource) throws DuplicateException {
		if (readResource(resource.getId()) != null) {
			// object with same ID exists already
			throw new org.opentdc.service.exception.DuplicateException();
		}
		// TODO: implement createResource
		logger.info("createResource() -> " + countResources() + " resources");
		throw new org.opentdc.service.exception.NotImplementedException(
			"method createResource is not yet implemented for opencrx storage");
		// logger.info("createResource() -> " + resource);
	}

	/**
	 * Find a Resource by ID.
	 * 
	 * @param id
	 *            the Resource ID
	 * @return the Resource
	 * @throws NotFoundException
	 *             if there exists no Resource with this ID
	 */
	@Override
	public ResourceModel readResource(String xri) throws NotFoundException {
		ResourceModel _resource = null;
		// TODO: implement readResource()
		throw new org.opentdc.service.exception.NotImplementedException(
			"method readResource() is not yet implemented for opencrx storage");
		// logger.info("readResource(" + xri + ") -> " + _resource);
	}

	@Override
	public ResourceModel updateResource(ResourceModel resource) throws NotFoundException {
		ResourceModel _resource = null;
		// TODO implement updateResource()
		throw new org.opentdc.service.exception.NotImplementedException(
				"method updateResource() is not yet implemented for opencrx storage.");
	}

	@Override
	public void deleteResource(String id) throws NotFoundException {
		// TODO implement deleteResource()
		throw new org.opentdc.service.exception.NotImplementedException(
				"method deleteResource() is not yet implemented for opencrx storage.");
	}

	@Override
	public int countResources() {
		int _count = -1;
		// TODO: implement countResources()
		throw new org.opentdc.service.exception.NotImplementedException(
				"method countResources() is not yet implemented for opencrx storage.");
		// logger.info("countResources() = " + _count);
		// return _count;
	}


	/******************************** utility methods *****************************************/
	/**
	 * Get persistence manager for configured user.
	 *
	 * @return the PersistenceManager
	 * @throws ServiceException
	 * @throws NamingException
	 */
	public PersistenceManager getPersistenceManager() {

		if (pmf == null) {
			try {
				pmf = org.opencrx.kernel.utils.Utils
						.getPersistenceManagerFactoryProxy(url, userName,
								password, mimeType);
			} catch (NamingException e) {
				e.printStackTrace();
			} catch (ServiceException e) {
				e.printStackTrace();
			}
		}
		return pmf.getPersistenceManager(userName, null);
	}

	/**
	 * Get activity segment.
	 * 
	 * @param pm
	 * @return
	 */
	public static org.opencrx.kernel.activity1.jmi1.Segment getActivitySegment(
			PersistenceManager pm) {
		return (org.opencrx.kernel.activity1.jmi1.Segment) pm
				.getObjectById(new Path(XRI_ACTIVITY_SEGMENT).getDescendant(
						"provider", providerName, "segment", segmentName));
	}
}

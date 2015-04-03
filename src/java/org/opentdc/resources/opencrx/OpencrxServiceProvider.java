package org.opentdc.resources.opencrx;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.jmi.reflect.DuplicateException;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.openmdx.base.exception.ServiceException;
import org.opentdc.opencrx.AbstractOpencrxServiceProvider;
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.NotFoundException;

public class OpencrxServiceProvider extends AbstractOpencrxServiceProvider implements ServiceProvider {
	
	public static final short ACTIVITY_GROUP_TYPE_PROJECT = 40;
	public static final short ACCOUNT_ROLE_CUSTOMER = 100;
	public static final short ACTIVITY_CLASS_INCIDENT = 2;
	public static final short ICAL_TYPE_NA = 0;
	public static final short ICAL_CLASS_NA = 0;
	public static final short ICAL_TYPE_VEVENT = 1;

	protected static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) throws ServiceException, NamingException {
		super(context, prefix);
	}

	/******************************** resource *****************************************/
	/**
	 * List all resources.
	 * 
	 * @return a list of all resources.
	 */
	@Override
	public ArrayList<ResourceModel> listResources(
		String queryType,
		String query,
		long position,
		long size
	) {
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
	public ResourceModel updateResource(
		String id,
		ResourceModel resource
	) throws NotFoundException {
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


}

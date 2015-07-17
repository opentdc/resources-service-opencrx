/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.resources.opencrx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.opencrx.kernel.account1.jmi1.Contact;
import org.opencrx.kernel.activity1.cci2.ResourceQuery;
import org.opencrx.kernel.activity1.jmi1.Resource;
import org.opencrx.kernel.utils.Utils;
import org.openmdx.base.exception.ServiceException;
import org.openmdx.base.persistence.cci.Queries;
import org.openmdx.base.rest.spi.Facades;
import org.openmdx.base.rest.spi.Query_2Facade;
import org.opentdc.opencrx.AbstractOpencrxServiceProvider;
import org.opentdc.opencrx.ActivitiesHelper;
import org.opentdc.resources.RateRefModel;
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;

/**
 * OpencrxServiceProvider
 *
 */
public class OpencrxServiceProvider extends AbstractOpencrxServiceProvider implements ServiceProvider {
	
	protected static final Logger logger = Logger.getLogger(OpencrxServiceProvider.class.getName());
	
	/**
	 * Constructor.
	 * 
	 * @param context
	 * @param prefix
	 * @throws ServiceException
	 * @throws NamingException
	 */
	public OpencrxServiceProvider(
		ServletContext context,
		String prefix
	) throws ServiceException, NamingException {
		super(context, prefix);
	}

	/**
	 * Map resource to resource model.
	 * 
	 * @param _resource
	 * @return
	 */
	protected ResourceModel mapToResource(
		Resource _resource
	) {
		ResourceModel resource = new ResourceModel();
		resource.setName(_resource.getName());
		resource.setCreatedAt(_resource.getCreatedAt());
		resource.setCreatedBy(_resource.getCreatedBy().get(0));
		resource.setModifiedAt(_resource.getModifiedAt());
		resource.setModifiedBy(_resource.getModifiedBy().get(0));
		resource.setId(_resource.refGetPath().getLastSegment().toClassicRepresentation());
		Contact contact = null;
		try {
			contact = _resource.getContact();
		} catch(Exception ignore) {}
		if(contact != null) {
			resource.setFirstName(_resource.getContact().getFirstName());
			resource.setLastName(_resource.getContact().getLastName());
			resource.setContactId(contact.refGetPath().getLastSegment().toClassicRepresentation());
		} else {
			String[] names = resource.getName().split(",");
			resource.setLastName(names.length > 0 ? names[0].trim() : "");
			resource.setFirstName(names.length > 1 ? names[1].trim() : "");
		}
		return resource;
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#listResources(java.lang.String, java.lang.String, long, long)
	 */
	@Override
	public List<ResourceModel> listResources(
		String queryType,
		String query,
		int position,
		int size
	) {
		try {
			PersistenceManager pm = this.getPersistenceManager();
			org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
			Query_2Facade resourcesQueryFacade = Facades.newQuery(null);
			resourcesQueryFacade.setQueryType(
				queryType == null || queryType.isEmpty()
					? "org:opencrx:kernel:activity1:Resource"
					: queryType
				);
			if(query != null && !query.isEmpty()) {
				resourcesQueryFacade.setQuery(query);
			}
			ResourceQuery resourcesQuery = (ResourceQuery)pm.newQuery(
				Queries.QUERY_LANGUAGE,
				resourcesQueryFacade.getDelegate()
			);
			resourcesQuery.forAllDisabled().isFalse();
			resourcesQuery.orderByName().ascending();
			resourcesQuery.thereExistsCategory().equalTo(ActivitiesHelper.RESOURCE_CATEGORY_PROJECT);
			List<Resource> resources = activitySegment.getResource(resourcesQuery);
			List<ResourceModel> result = new ArrayList<ResourceModel>();
			int count = 0;
			for(Iterator<Resource> i = resources.listIterator(position); i.hasNext(); ) {
				Resource resource = i.next();
				result.add(this.mapToResource(resource));
				count++;
				if(count >= size) {
					break;
				}
			}
			return result;
		} catch(ServiceException e) {
			e.log();
			throw new InternalServerErrorException(e.getMessage());
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#createResource(org.opentdc.resources.ResourceModel)
	 */
	@Override
	public ResourceModel createResource(
		ResourceModel resource
	) throws DuplicateException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		org.opencrx.kernel.account1.jmi1.Segment accountSegment = this.getAccountSegment();
		if(resource.getId() != null) {
			Resource _resource = null;
			try {
				_resource = activitySegment.getResource(resource.getId()); 
			} catch(Exception ignore) {}
			if(_resource != null) {
				throw new DuplicateException("Resource with ID " + resource.getId() + " exists already.");			
			} else {
				throw new ValidationException("Resource <" + resource.getId() + "> contains an ID generated on the client. This is not allowed.");
			}
		}
		if(resource.getName() == null || resource.getName().isEmpty()) {
			throw new ValidationException("resource must have a valid name.");
		}
		if(resource.getFirstName() == null || resource.getFirstName().isEmpty()) {
			throw new ValidationException("resource must have a valid firstName.");
		}
		if(resource.getLastName() == null || resource.getLastName().isEmpty()) {
			throw new ValidationException("resource must have a valid lastName.");
		}
		if(resource.getContactId() == null || resource.getContactId().isEmpty()) {
			throw new ValidationException("resource must have a valid contactId.");
		}
		Contact contact = (Contact)accountSegment.getAccount(resource.getContactId());
		if(contact == null) {
			if(resource.getContactId() == null || resource.getContactId().isEmpty()) {
				throw new ValidationException("resource must have a valid contactId.");
			}			
		}
		// Create resource
		Resource _resource = null;
		{
			_resource = pm.newInstance(Resource.class);
			if(resource.getName() == null || resource.getName().isEmpty()) {
				if(contact == null) {
					_resource.setName(resource.getLastName() + ", " + resource.getFirstName());
				} else {
					_resource.setName(contact.getFullName());
				}
			} else {
				_resource.setName(resource.getName());
			}
			_resource.setContact(contact);
			_resource.getCategory().add(ActivitiesHelper.RESOURCE_CATEGORY_PROJECT);
			try {
				pm.currentTransaction().begin();
				activitySegment.addResource(
					Utils.getUidAsString(),
					_resource
				);
				pm.currentTransaction().commit();
				return this.readResource(_resource.refGetPath().getLastSegment().toClassicRepresentation());
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to create resource");
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#readResource(java.lang.String)
	 */
	@Override
	public ResourceModel readResource(
		String id
	) throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = null;
		try {
			resource = activitySegment.getResource(id);
		} catch(Exception ignore) {}
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(id);
		} else {
			return this.mapToResource(resource);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#updateResource(java.lang.String, org.opentdc.resources.ResourceModel)
	 */
	@Override
	public ResourceModel updateResource(
		String id,
		ResourceModel resource
	) throws NotFoundException, ValidationException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		org.opencrx.kernel.account1.jmi1.Segment accountSegment = this.getAccountSegment();		
		Resource _resource = activitySegment.getResource(id);
		if(_resource == null) {
			throw new org.opentdc.service.exception.NotFoundException(id);
		} else {
			try {
				pm.currentTransaction().begin();
				_resource.setName(resource.getName());
				Contact contact = (Contact)accountSegment.getAccount(resource.getContactId());
				_resource.setContact(contact);
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to update resource");
			}
			return this.readResource(id);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#deleteResource(java.lang.String)
	 */
	@Override
	public void deleteResource(
		String id
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = null;
		try {
			resource = activitySegment.getResource(id);
		} catch(Exception ignore) {}
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(id);
		} else {
			try {
				pm.currentTransaction().begin();
				resource.setDisabled(true);
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to delete resource");
			}
		}
	}

	@Override
	public List<RateRefModel> listRateRefs(String resourceId, String queryType,
			String query, int position, int size) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RateRefModel createRateRef(String resourceId, RateRefModel rateRef)
			throws DuplicateException, ValidationException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RateRefModel readRateRef(String resourceId, String rateRefId)
			throws NotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void deleteRateRef(String resourceId, String rateRefId)
			throws NotFoundException, InternalServerErrorException {
		// TODO Auto-generated method stub
		
	}
}

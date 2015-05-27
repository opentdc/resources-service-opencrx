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
import javax.jmi.reflect.DuplicateException;
import javax.naming.NamingException;
import javax.servlet.ServletContext;

import org.opencrx.kernel.account1.cci2.ContactQuery;
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
import org.opentdc.resources.ResourceModel;
import org.opentdc.resources.ServiceProvider;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotFoundException;

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
	 * @param resource
	 * @return
	 */
	protected ResourceModel newResourceModel(
		Resource resource
	) {
		ResourceModel _r = new ResourceModel();
		_r.setName(resource.getName());
		if(resource.getContact() != null) {
			_r.setFirstName(resource.getContact().getFirstName());
			_r.setLastName(resource.getContact().getLastName());
		}
		return _r;
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
				result.add(this.newResourceModel(resource));
				count++;
				if(count > size) {
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
		ResourceModel r
	) throws DuplicateException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		org.opencrx.kernel.account1.jmi1.Segment accountSegment = this.getAccountSegment();
		if(r.getId() != null) {
			Resource resource = null;
			try {
				resource = activitySegment.getResource(r.getId()); 
			} catch(Exception ignore) {}
			if(resource != null) {
				throw new org.opentdc.service.exception.DuplicateException();
			}
		}
		PersistenceManager pm = this.getPersistenceManager();
		Contact contact = null;
		// Find contact matching firstName, lastName
		{
			ContactQuery contactQuery = (ContactQuery)pm.newQuery(Contact.class);
			contactQuery.thereExistsLastName().equalTo(r.getLastName());
			contactQuery.thereExistsFirstName().equalTo(r.getFirstName());
			contactQuery.forAllDisabled().isFalse();
			List<Contact> contacts = accountSegment.getAccount(contactQuery);
			if(contacts.isEmpty()) {
				contact = pm.newInstance(Contact.class);
				contact.setFirstName(r.getFirstName());
				contact.setLastName(r.getLastName());
				try {
					pm.currentTransaction().begin();
					accountSegment.addAccount(
						Utils.getUidAsString(),
						contact
					);
					pm.currentTransaction().commit();
				} catch(Exception e) {
					new ServiceException(e).log();
					try {
						pm.currentTransaction().rollback();
					} catch(Exception ignore) {}
				}
			} else {
				contact = contacts.iterator().next();
			}
		}
		// Create resource
		Resource resource = null;
		{
			resource = pm.newInstance(Resource.class);
			if(r.getName() == null || r.getName().isEmpty()) {
				if(contact == null) {
					resource.setName(r.getLastName() + ", " + r.getFirstName());
				} else {
					resource.setName(contact.getFullName());
				}
			} else {
				resource.setName(r.getName());
			}
			resource.setContact(contact);
			resource.getCategory().add(ActivitiesHelper.RESOURCE_CATEGORY_PROJECT);
			try {
				pm.currentTransaction().begin();
				activitySegment.addResource(
					Utils.getUidAsString(),
					resource
				);
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to create resource");
			}
		}
		return this.newResourceModel(resource);
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
		if(resource == null) {
			throw new org.opentdc.service.exception.NotFoundException(id);
		} else {
			return this.newResourceModel(resource);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#updateResource(java.lang.String, org.opentdc.resources.ResourceModel)
	 */
	@Override
	public ResourceModel updateResource(
		String id,
		ResourceModel r
	) throws NotFoundException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = activitySegment.getResource(id);
		if(resource == null) {
			throw new org.opentdc.service.exception.NotFoundException(id);
		} else {
			try {
				pm.currentTransaction().begin();
				resource.setName(r.getName());
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to update resource");
			}
			return this.newResourceModel(resource);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#deleteResource(java.lang.String)
	 */
	@Override
	public void deleteResource(
		String id
	) throws NotFoundException {
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
}

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
import org.opencrx.kernel.activity1.cci2.ResourceRateQuery;
import org.opencrx.kernel.activity1.jmi1.Resource;
import org.opencrx.kernel.activity1.jmi1.ResourceRate;
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
 * Resources service for openCRX.
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

	/**
	 * Map resource rate to rate ref.
	 * 
	 * @param resourceRate
	 * @return
	 */
	protected RateRefModel mapToRateRef(
		ResourceRate resourceRate
	) {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		RateRefModel rateRef = new RateRefModel();
		rateRef.setCreatedAt(resourceRate.getCreatedAt());
		rateRef.setCreatedBy(resourceRate.getCreatedBy().get(0));
		rateRef.setId(resourceRate.refGetPath().getLastSegment().toClassicRepresentation());
		if(resourceRate.getName().startsWith("#")) {
			String rateId = resourceRate.getName().substring(1);
			rateRef.setRateId(rateId);
			Resource ratesResource = ActivitiesHelper.findRatesResource(activitySegment);
			rateRef.setRateTitle(ratesResource.getResourceRate(rateId).getName());
		}
		return rateRef;
	}

	/**
	 * Get resource with given id.
	 * 
	 * @param activitySegment
	 * @param id
	 * @return
	 */
	protected Resource getResource(
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment,
		String id
	) {
		Resource resource = null;
		try {
			resource = activitySegment.getResource(id);
		} catch(Exception ignore) {}
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
		Resource resource = this.getResource(activitySegment, id);
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
		Resource _resource = this.getResource(activitySegment, id);
		if(_resource == null || Boolean.TRUE.equals(_resource.isDisabled())) {
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
		Resource resource = this.getResource(activitySegment, id);
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

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#listRateRefs(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	@Override
	public List<RateRefModel> listRateRefs(
		String resourceId, 
		String queryType,
		String query, 
		int position, 
		int size
	) {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = this.getResource(activitySegment, resourceId);
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(resourceId);
		} else {		
			try {
				ResourceRateQuery resourceRateQuery = (ResourceRateQuery)pm.newQuery(ResourceRate.class);
				resourceRateQuery.forAllDisabled().isFalse();
				resourceRateQuery.orderByName().ascending();
				List<ResourceRate> resourceRates = resource.getResourceRate(resourceRateQuery);
				List<RateRefModel> result = new ArrayList<RateRefModel>();
				int count = 0;
				for(Iterator<ResourceRate> i = resourceRates.listIterator(position); i.hasNext(); ) {
					ResourceRate resourceRate = i.next();
					result.add(this.mapToRateRef(resourceRate));
					count++;
					if(count >= size) {
						break;
					}
				}
				return result;
			} catch(Exception e) {
				new ServiceException(e).log();
				throw new InternalServerErrorException(e.getMessage());
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#createRateRef(java.lang.String, org.opentdc.resources.RateRefModel)
	 */
	@Override
	public RateRefModel createRateRef(
		String resourceId, 
		RateRefModel rateRef
	) throws DuplicateException, ValidationException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = this.getResource(activitySegment, resourceId);
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(resourceId);
		} else {
			if(rateRef.getId() != null) {
				ResourceRate resourceRate = null;
				try {
					resourceRate = resource.getResourceRate(rateRef.getId());
				} catch(Exception ignore) {}
				if(resourceRate != null) {
					throw new DuplicateException("RateRef with ID " + rateRef.getId() + " exists already.");			
				} else {
					throw new ValidationException("RateRef <" + rateRef.getId() + "> contains an ID generated on the client. This is not allowed.");
				}
			}
			if (rateRef.getRateId() == null || rateRef.getRateId().isEmpty()) {
				throw new ValidationException("RateRefModel <" + resourceId + "> must contain a valid rateId.");
			}			
			ResourceRate resourceRate = null;
			try {
				pm.currentTransaction().begin();
				resourceRate = pm.newInstance(ResourceRate.class);
				resourceRate.setName("#" + rateRef.getRateId());
				ResourceRate referencedRate = ActivitiesHelper.findRatesResource(activitySegment).getResourceRate(rateRef.getRateId());
				if(referencedRate != null) {
					resourceRate.setDescription(referencedRate.getDescription());
					resourceRate.setRate(referencedRate.getRate());
					resourceRate.setRateType(referencedRate.getRateType());
					resourceRate.setRateCurrency(referencedRate.getRateCurrency());
				}
				resource.addResourceRate(
					Utils.getUidAsString(),
					resourceRate
				);
				pm.currentTransaction().commit();
				return this.readRateRef(
					resource.refGetPath().getLastSegment().toClassicRepresentation(),
					resourceRate.refGetPath().getLastSegment().toClassicRepresentation()
				);
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to create rate ref");
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#readRateRef(java.lang.String, java.lang.String)
	 */
	@Override
	public RateRefModel readRateRef(
		String resourceId, 
		String rateRefId
	) throws NotFoundException {
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = this.getResource(activitySegment, resourceId);
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(resourceId);
		} else {
			ResourceRate resourceRate = resource.getResourceRate(rateRefId);
			if(resourceRate == null || Boolean.TRUE.equals(resourceRate.isDisabled())) {
				throw new org.opentdc.service.exception.NotFoundException(resourceId);				
			}
			return this.mapToRateRef(resourceRate);
		}
	}

	/* (non-Javadoc)
	 * @see org.opentdc.resources.ServiceProvider#deleteRateRef(java.lang.String, java.lang.String)
	 */
	@Override
	public void deleteRateRef(
		String resourceId, 
		String rateRefId
	) throws NotFoundException, InternalServerErrorException {
		PersistenceManager pm = this.getPersistenceManager();
		org.opencrx.kernel.activity1.jmi1.Segment activitySegment = this.getActivitySegment();
		Resource resource = this.getResource(activitySegment, resourceId);
		if(resource == null || Boolean.TRUE.equals(resource.isDisabled())) {
			throw new org.opentdc.service.exception.NotFoundException(resourceId);
		} else {
			ResourceRate resourceRate = resource.getResourceRate(rateRefId);
			if(resourceRate == null || Boolean.TRUE.equals(resourceRate.isDisabled())) {
				throw new org.opentdc.service.exception.NotFoundException(resourceId);				
			}
			try {
				pm.currentTransaction().begin();
				resourceRate.setDisabled(true);
				pm.currentTransaction().commit();
			} catch(Exception e) {
				new ServiceException(e).log();
				try {
					pm.currentTransaction().rollback();
				} catch(Exception ignore) {}
				throw new InternalServerErrorException("Unable to delete rate ref");
			}
		}
	}
	
}

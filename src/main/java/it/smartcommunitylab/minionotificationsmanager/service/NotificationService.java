package it.smartcommunitylab.minionotificationsmanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.smartcommunitylab.minionotificationsmanager.common.NoSuchBucketException;
import it.smartcommunitylab.minionotificationsmanager.common.NoSuchEventException;
import it.smartcommunitylab.minionotificationsmanager.common.SystemException;
import it.smartcommunitylab.minionotificationsmanager.minio.MinioBridge;
import it.smartcommunitylab.minionotificationsmanager.minio.MinioException;
import it.smartcommunitylab.minionotificationsmanager.model.Event;
import it.smartcommunitylab.minionotificationsmanager.model.EventDTO;
import it.smartcommunitylab.minionotificationsmanager.repository.EventRepository;

@Component
public class NotificationService {
    private final static Logger _log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    MinioBridge minio;

    @Autowired
    private EventRepository repository;

    /*
     * CRUD
     */

    public Event getEvent(String bucket, long id) throws NoSuchEventException, SystemException {
        if (repository.existsById(id)) {
            _log.debug("get event with id " + String.valueOf(id));
            Event e = repository.getOne(id);

            // check if bucket match
            if (bucket.equals(e.getBucket())) {
                return e;
            } else {
                throw new NoSuchEventException();
            }
        } else {
            throw new NoSuchEventException();
        }
    }

    public Event addEvent(String bucket, String[] actions, String prefix, String suffix)
            throws NoSuchBucketException, SystemException {
        try {
            // check if bucket exists in minio
            boolean exists = minio.hasBucket(bucket);

            if (!exists) {
                _log.error("bucket " + bucket + " does not exists in minio");
                throw new NoSuchBucketException();
            }

            // build as DTO and then convert
            EventDTO dto = new EventDTO();
            dto.setBucket(bucket);
            dto.setActions(actions);
            dto.setPrefix(prefix);
            dto.setSuffix(suffix);
            
            
            //check overlapping
            

            _log.debug("add event " + dto.toString());
            Event event = dto.toEvent();
            event.setImported(false);
            // save to repository
            // listener will register to minio

            return repository.saveAndFlush(event);
        } catch (MinioException e) {
            throw new SystemException(e.getMessage());
        }
    }

    public Event deleteEvent(String bucket, long id) throws NoSuchEventException, SystemException {
//        try {
        // first unregister if present
        if (repository.existsById(id)) {
            _log.debug("delete event " + String.valueOf(id));
            Event e = repository.getOne(id);
            // check if bucket match
            if (bucket.equals(e.getBucket())) {
                // unregister and *then* remove
                unregisterEvent(e);
                repository.delete(e);

                return e;
            } else {
                throw new NoSuchEventException();
            }

        } else {
            throw new NoSuchEventException();
        }
//        } catch (Exception e) {
//            throw new SystemException(e.getMessage());
//        }
    }

    public long countEvents(String bucket) throws SystemException {
        _log.debug("count events for bucket " + bucket);
        try {
            return repository.countByBucket(bucket);
        } catch (Exception e) {
            throw new SystemException(e.getMessage());
        }
    }

    public List<Event> listEvents(String bucket) throws SystemException {
        _log.debug("list events for bucket " + bucket);
        try {
            return repository.findByBucket(bucket);
//        List<Event> events = repository.findByBucket(bucket);
//        // convert to DTO to build topic names
//        return events.stream().map(e -> EventDTO.fromEvent(e)).collect(Collectors.toList());
        } catch (Exception e) {
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * Actions
     */

    public void registerEvent(Event event) throws SystemException {
        _log.debug("register event " + event.toString());
        EventDTO dto = EventDTO.fromEvent(event);
        try {
            // register handles duplicates
            minio.registerEvent(dto);
        } catch (MinioException e) {
            _log.error("error registering event: " + e.getMessage());
            e.printStackTrace();
            throw new SystemException(e.getMessage());

        }
    }

    public void unregisterEvent(Event event) throws SystemException {
        _log.debug("unregister event " + event.toString());
        EventDTO dto = EventDTO.fromEvent(event);
        // search for duplicates and remove from minio only if none found
        List<Event> events = repository.findByBucket(event.getBucket());
        int c = 0;
        for (Event e : events) {
            // compare via DTOs
            if (dto.equals(EventDTO.fromEvent(e))) {
                c++;
            }
        }

        if (c <= 1) {
            try {
                // unregister
                minio.unregisterEvent(dto);
            } catch (MinioException e) {
                _log.error("error unregistering event: " + e.getMessage());
                e.printStackTrace();

                throw new SystemException(e.getMessage());
            }
        }
    }

    public List<Event> syncToMinio(String bucket, boolean clear) throws SystemException {
        _log.debug("sync events to minio for bucket " + bucket);
        List<Event> events = repository.findByBucket(bucket);
        List<EventDTO> dtos = events.stream().map(e -> EventDTO.fromEvent(e))
                .collect(Collectors.toList());
        try {
            // register handles duplicates
            minio.setEvents(bucket, dtos, clear);
        } catch (MinioException mex) {
            _log.error("error sync bucket: " + mex.getMessage());
            mex.printStackTrace();
            throw new SystemException(mex.getMessage());

        }

        // return all local events as exported
        return events;

    }

    public List<Event> syncFromMinio(String bucket, boolean clear) throws SystemException {
        _log.debug("sync events from minio for bucket " + bucket);

        if (clear) {
            // delete local events for bucket
            List<Event> events = repository.findByBucket(bucket);
            for (Event e : events) {
                repository.delete(e);
            }
        }

        List<EventDTO> events = repository.findByBucket(bucket)
                .stream().map(e -> EventDTO.fromEvent(e))
                .collect(Collectors.toList());

        List<Event> results = new ArrayList<>();

        try {
            // resolve duplicates
            List<EventDTO> list = minio.getEvents(bucket);
            for (EventDTO e : list) {
                // will match on topic as per equals()
                if (!events.contains(e)) {
                    // add as new
                    // will trigger register but flag will disable propagation
                    _log.debug("add event " + e.toString());
                    Event event = e.toEvent();
                    event.setImported(true);
                    event = repository.saveAndFlush(event);
                    results.add(event);
                }
            }

        } catch (MinioException mex) {
            _log.error("error sync bucket: " + mex.getMessage());
            mex.printStackTrace();
            throw new SystemException(mex.getMessage());

        }

        // return only newly added events
        return results;

    }

    /*
     * Sync
     */
    public void syncFromMinio(boolean clear) throws SystemException {
        _log.debug("sync all events from minio");

        try {
            // fetch all buckets from minio
            List<String> buckets = minio.listBuckets();
            for (String bucket : buckets) {
                List<Event> events = syncFromMinio(bucket, clear);
            }
        } catch (MinioException mex) {
            mex.printStackTrace();
            _log.error("error sync from minio: " + mex.getMessage());
            throw new SystemException(mex.getMessage());
        }
    }

    public void syncToMinio(boolean clear) throws SystemException {
        _log.debug("sync all events to minio");

        try {
            // fetch all buckets from minio
            List<String> buckets = minio.listBuckets();
            for (String bucket : buckets) {
                List<Event> events = syncToMinio(bucket, clear);
            }
        } catch (MinioException mex) {
            mex.printStackTrace();
            _log.error("error sync to minio: " + mex.getMessage());
            throw new SystemException(mex.getMessage());
        }
    }
}

package it.smartcommunitylab.minionotificationsmanager.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.minio.messages.EventType;
import it.smartcommunitylab.minionotificationsmanager.common.InvalidArgumentException;
import it.smartcommunitylab.minionotificationsmanager.common.NoSuchBucketException;
import it.smartcommunitylab.minionotificationsmanager.common.NoSuchEventException;
import it.smartcommunitylab.minionotificationsmanager.common.SystemException;
import it.smartcommunitylab.minionotificationsmanager.model.Event;
import it.smartcommunitylab.minionotificationsmanager.model.EventDTO;
import it.smartcommunitylab.minionotificationsmanager.service.NotificationService;

@RestController
public class EventsController {

    private final static Logger _log = LoggerFactory.getLogger(EventsController.class);

    @Autowired
    NotificationService service;

    @GetMapping(value = "/api/events/{bucket}", produces = "application/json")
    @ResponseBody
    public List<EventDTO> list(
            @PathVariable("bucket") String bucket,
            HttpServletRequest request, HttpServletResponse response,
            Pageable pageable) throws SystemException {

        _log.debug("list events for " + bucket);

        long total = service.countEvents(bucket);
        List<Event> events = service.listEvents(bucket);

        // add total count as header
        response.setHeader("X-Total-Count", String.valueOf(total));

        // convert to DTO to build topic names
        return events.stream().map(e -> EventDTO.fromEvent(e)).collect(Collectors.toList());
    }

    @PostMapping(value = "/api/events/{bucket}", produces = "application/json")
    @ResponseBody
    public EventDTO add(
            @PathVariable("bucket") String bucket,
            @RequestBody EventDTO event,
            HttpServletRequest request, HttpServletResponse response)
            throws InvalidArgumentException, NoSuchBucketException, SystemException {

        // override bucket in body with path value
        event.setBucket(bucket);

        if (!event.isValid()) {
            throw new InvalidArgumentException("missing fields");
        }

        // validate actions via event types, will throw exception
        try {
            List<EventType> types = EventType.fromStringList(Arrays.asList(event.getActions()));
        } catch (io.minio.errors.InvalidArgumentException eax) {
            throw new InvalidArgumentException(eax.getMessage());
        }

        _log.debug("add event for bucket " + bucket + " for actions " + Arrays.toString(event.getActions()));

        // add via service
        Event e = service.addEvent(event.getBucket(), event.getActions(), event.getPrefix(), event.getSuffix());

        // return as DTO to strip private fields and populate topic name
        return EventDTO.fromEvent(e);

    }

    @GetMapping(value = "/api/events/{bucket}/{id}", produces = "application/json")
    @ResponseBody
    public EventDTO get(
            @PathVariable("bucket") String bucket,
            @PathVariable("id") long id,
            HttpServletRequest request, HttpServletResponse response)
            throws NoSuchEventException, SystemException {

        _log.debug("get event " + String.valueOf(id));

        // will trigger exception if not found
        Event e = service.getEvent(bucket, id);

        // return as DTO to strip private fields and populate topic name
        return EventDTO.fromEvent(e);
    }

    @DeleteMapping(value = "/api/events/{bucket}/{id}", produces = "application/json")
    @ResponseBody
    public EventDTO delete(
            @PathVariable("bucket") String bucket,
            @PathVariable("id") long id,
            HttpServletRequest request, HttpServletResponse response)
            throws NoSuchEventException, Exception {

        _log.debug("delete event " + String.valueOf(id));

        // will trigger exception if not found
        Event e = service.deleteEvent(bucket, id);

        // return as DTO to strip private fields and populate topic name
        return EventDTO.fromEvent(e);
    }

    /*
     * Sync
     */

    @PostMapping(value = "/api/events/{bucket}/import", produces = "application/json")
    @ResponseBody
    public List<EventDTO> importFromMinio(
            @PathVariable("bucket") String bucket,
            HttpServletRequest request, HttpServletResponse response)
            throws InvalidArgumentException, NoSuchBucketException, SystemException {

        // disable clear
        boolean clear = false;
        _log.debug("import all events for bucket " + bucket);

        // via service
        List<Event> events = service.syncFromMinio(bucket, clear);

        // convert to DTO to build topic names
        return events.stream().map(e -> EventDTO.fromEvent(e)).collect(Collectors.toList());

    }

    @PostMapping(value = "/api/events/{bucket}/export", produces = "application/json")
    @ResponseBody
    public List<EventDTO> exportToMinio(
            @PathVariable("bucket") String bucket,
            HttpServletRequest request, HttpServletResponse response)
            throws InvalidArgumentException, NoSuchBucketException, SystemException {

        // disable clear
        boolean clear = false;
        _log.debug("export all events for bucket " + bucket);

        // via service
        List<Event> events = service.syncToMinio(bucket, clear);

        // convert to DTO to build topic names
        return events.stream().map(e -> EventDTO.fromEvent(e)).collect(Collectors.toList());

    }

    /*
     * Types
     */
    @GetMapping(value = "/api/event-types", produces = "application/json")
    @ResponseBody
    public String[] types(
            HttpServletRequest request, HttpServletResponse response)
            throws SystemException {
        // list all event types for minio
        return Arrays.stream(EventType.values()).map(t -> t.toString()).toArray(String[]::new);

    }

    /*
     * Exceptions
     */

    @ExceptionHandler(NoSuchEventException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public String notFound(NoSuchEventException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(NoSuchBucketException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public String notFound(NoSuchBucketException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(InvalidArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public String invalidRequest(InvalidArgumentException ex) {
        return ex.getMessage();
    }

    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public String systemError(SystemException ex) {
        return ex.getMessage();
    }

}

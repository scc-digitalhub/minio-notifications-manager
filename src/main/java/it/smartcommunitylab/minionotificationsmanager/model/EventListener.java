package it.smartcommunitylab.minionotificationsmanager.model;

import javax.persistence.PostPersist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.smartcommunitylab.minionotificationsmanager.minio.MinioBridge;
import it.smartcommunitylab.minionotificationsmanager.minio.MinioException;

@Component
public class EventListener {
    private final static Logger _log = LoggerFactory.getLogger(EventListener.class);

    // autowired here does NOT work since JPA listeners are stateless
    private MinioBridge minio;

    @Autowired
    public EventListener(MinioBridge bean) {
        // leverage constructor to autowire service
        this.minio = bean;
    }

    @PostPersist
    private void postPersist(final Event event) {
        _log.debug("postPersist event " + event.toString());
        // register directly to minio
        // TODO evaluate using applicationEvents via spring to decouple
        try {
            if (!event.isImported()) {
                // need to convert event to detach and format
                minio.registerEvent(EventDTO.fromEvent(event));
            }
        } catch (MinioException mex) {
            // ignore to avoid bubbling error to persistence
            mex.printStackTrace();
        }
//        // register via service to minio
//        //does NOT work due to circular dep
//        try {
//            // need to duplicate event otherwise service will receive null
//            // TODO evaluate using applicationEvents via spring to decouple
//            Event e = EventDTO.fromEvent(event).toEvent();
//            _log.debug("postPersist e " + e.toString());
//            if (service == null) {
//                _log.error("service null");
//            }
//            service.registerEvent(e);
//        } catch (SystemException ex) {
//            // ignore to avoid bubbling error to persistence
//            ex.printStackTrace();
//        }

    }

}

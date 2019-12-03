package it.smartcommunitylab.minionotificationsmanager.minio;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xmlpull.v1.XmlPullParserException;

import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidArgumentException;
import io.minio.errors.InvalidBucketNameException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidObjectPrefixException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.NoResponseException;
import io.minio.messages.EventType;
import io.minio.messages.Filter;
import io.minio.messages.FilterRule;
import io.minio.messages.NotificationConfiguration;
import io.minio.messages.QueueConfiguration;
import it.smartcommunitylab.minionotificationsmanager.model.EventDTO;

@Component
public class MinioBridge {
    private final static Logger _log = LoggerFactory.getLogger(MinioBridge.class);

    @Value("${minio.endpoint}")
    private String ENDPOINT;

    @Value("${minio.port}")
    private int PORT;

    @Value("${minio.secure}")
    private boolean SECURE;

    @Value("${minio.region}")
    private String REGION;

    @Value("${minio.accessKey}")
    private String ACCESS_KEY;

    @Value("${minio.secretKey}")
    private String SECRET_KEY;

    @Value("${minio.queue}")
    private String QUEUE;

    /*
     * Buckets
     */

    public boolean hasBucket(String name) throws MinioException {
        try {
            MinioClient minio = getClient(name);
            return minio.bucketExists(name);
        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }
    }

    public List<String> listBuckets() throws MinioException {
        try {
            MinioClient minio = getClient();
            return minio.listBuckets().stream().map(b -> b.name()).collect(Collectors.toList());
        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }
    }

    /*
     * Events
     */

    public void registerEvent(EventDTO event) throws MinioException {
        try {
            String bucket = event.getBucket();
            MinioClient minio = getClient(bucket);

            // get current notificationConfiguration for bucket
            NotificationConfiguration notificationConfiguration = minio.getBucketNotification(bucket);
            List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();

            // search for duplicates
            QueueConfiguration queueConfiguration = null;
            for (QueueConfiguration qc : queueConfigurationList) {
                // derive event mapping
                EventDTO e = queueConfigurationToEvent(bucket, qc);
//                _log.debug("compare " + event.toString() + " to " + e.toString());

                if (QUEUE.equals(qc.queue()) && event.equals(e)) {
                    // duplicate
                    queueConfiguration = qc;
                    break;
                }
            }

            if (queueConfiguration == null) {
                // build as new
                queueConfiguration = eventToQueueConfiguration(event);
                queueConfiguration.setQueue(QUEUE);

                queueConfigurationList.add(queueConfiguration);
                notificationConfiguration.setQueueConfigurationList(queueConfigurationList);

                // Set updated notification configuration.
                minio.setBucketNotification(bucket, notificationConfiguration);
            }
        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | InvalidObjectPrefixException | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException | InvalidArgumentException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }

    }

    public void unregisterEvent(EventDTO event) throws MinioException {
        try {
            String bucket = event.getBucket();
            MinioClient minio = getClient(bucket);

            // get current notificationConfiguration for bucket
            NotificationConfiguration notificationConfiguration = minio.getBucketNotification(bucket);
            List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();

            // iterate over all configurations to find a match for event
            // multiple checks to minimize work to de-serialize configuration
            for (QueueConfiguration queueConfiguration : queueConfigurationList) {
                // first match queue
                if (QUEUE.equals(queueConfiguration.queue())) {
                    List<EventType> eventList = EventType.fromStringList(Arrays.asList(event.getActions()));
                    // match event types
                    if (new HashSet<>(eventList).equals(new HashSet<>(queueConfiguration.events()))) {
                        // match attributes
                        // need to recover s3 rules from filter
                        List<FilterRule> rules = queueConfiguration.filter().s3Key().filterRuleList();
                        String prefix = "";
                        String suffix = "";
                        for (FilterRule filterRule : rules) {
                            if ("prefix".equals(filterRule.name())) {
                                prefix = filterRule.value();
                            }
                            if ("suffix".equals(filterRule.name())) {
                                suffix = filterRule.value();
                            }
                        }

                        if (event.getPrefix().equals(prefix) && event.getSuffix().equals(suffix)) {
                            // match, remove
                            queueConfigurationList.remove(queueConfiguration);
                        }
                    }
                }
            }

            // update minio
            notificationConfiguration.setQueueConfigurationList(queueConfigurationList);
            minio.setBucketNotification(bucket, notificationConfiguration);

        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | InvalidObjectPrefixException | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException | InvalidArgumentException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }

    }

    /*
     * Two-way sync
     */

    public List<EventDTO> getEvents(String bucket) throws MinioException {
        try {
            MinioClient minio = getClient(bucket);

            // get current notificationConfiguration for bucket
            NotificationConfiguration notificationConfiguration = minio.getBucketNotification(bucket);
            List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();

            List<EventDTO> events = new ArrayList<>();
            // fetch from config if queue matches
            for (QueueConfiguration qc : queueConfigurationList) {
                if (QUEUE.equals(qc.queue())) {
                    // derive event mapping
                    EventDTO e = queueConfigurationToEvent(bucket, qc);
                    events.add(e);
                }
            }

            return events;
        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | InvalidObjectPrefixException | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException | InvalidArgumentException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }
    }

    public void setEvents(String bucket, List<EventDTO> events, boolean deleteExisting) throws MinioException {
        try {
            MinioClient minio = getClient(bucket);

            // get current notificationConfiguration for bucket
            NotificationConfiguration notificationConfiguration = minio.getBucketNotification(bucket);
            List<QueueConfiguration> queueConfigurationList = notificationConfiguration.queueConfigurationList();

            if (deleteExisting) {
                for (QueueConfiguration qc : queueConfigurationList) {
                    if (QUEUE.equals(qc.queue())) {
                        queueConfigurationList.remove(qc);
                    }
                }
            }

            // search for duplicates
            List<EventDTO> current = new ArrayList<>();
            for (QueueConfiguration qc : queueConfigurationList) {
                if (QUEUE.equals(qc.queue())) {
                    // derive event mapping
                    EventDTO e = queueConfigurationToEvent(bucket, qc);
                    current.add(e);
                }
            }

            // dedup via set to clear local duplicates
            Set<EventDTO> toSet = new HashSet<>();
            toSet.addAll(events);

            // dedup via single pass
            List<EventDTO> toAdd = new ArrayList<>();
            for (EventDTO e : toSet) {
                if (!current.contains(e)) {
                    toAdd.add(e);
                }
            }

            // build new configs
            for (EventDTO e : toAdd) {
                // build as new
                QueueConfiguration queueConfiguration = eventToQueueConfiguration(e);
                queueConfiguration.setQueue(QUEUE);
                queueConfigurationList.add(queueConfiguration);
            }

            notificationConfiguration.setQueueConfigurationList(queueConfigurationList);

            // Set updated notification configuration.
            minio.setBucketNotification(bucket, notificationConfiguration);

        } catch (InvalidEndpointException | InvalidPortException | InvalidKeyException | InvalidBucketNameException
                | InvalidObjectPrefixException | NoSuchAlgorithmException | InsufficientDataException
                | NoResponseException | ErrorResponseException | InternalException | InvalidResponseException
                | IOException | XmlPullParserException | InvalidArgumentException e) {
            e.printStackTrace();
            throw new MinioException(e);
        }

    }

    /*
     * Client
     */

    private MinioClient getClient(String bucket) throws InvalidEndpointException, InvalidPortException {
        // TODO implement dynamic via STS
        // use global credentials
        return getClient();

    }

    private MinioClient getClient() throws InvalidEndpointException, InvalidPortException {
        // use global credentials
        _log.debug("create client for " + ENDPOINT + ":" + String.valueOf(PORT) + " with accessKey " + ACCESS_KEY);
        if (StringUtils.isEmpty(REGION)) {
            return new MinioClient(ENDPOINT, PORT, ACCESS_KEY, SECRET_KEY, REGION, SECURE);
        } else {
            return new MinioClient(ENDPOINT, PORT, ACCESS_KEY, SECRET_KEY, SECURE);

        }

    }

    /*
     * Mappers
     */

    private EventDTO queueConfigurationToEvent(String bucket, QueueConfiguration queueConfiguration)
            throws InvalidArgumentException {
        EventDTO e = new EventDTO();
        e.setBucket(bucket);

        // parse eventList
        List<EventType> eventList = queueConfiguration.events();
        e.setActions(EventType.toStringList(eventList).toArray(new String[0]));

        // need to recover s3 rules from filter
        List<FilterRule> rules = queueConfiguration.filter().s3Key().filterRuleList();
        String prefix = "";
        String suffix = "";
        for (FilterRule filterRule : rules) {
            if ("prefix".equals(filterRule.name())) {
                prefix = filterRule.value();
            }
            if ("suffix".equals(filterRule.name())) {
                suffix = filterRule.value();
            }
        }

        e.setPrefix(prefix);
        e.setSuffix(suffix);

        // need to explicitly build topic to enable comparison
        e.buildTopic();
        return e;

    }

    private QueueConfiguration eventToQueueConfiguration(EventDTO e)
            throws InvalidArgumentException, XmlPullParserException {
        QueueConfiguration queueConfiguration = new QueueConfiguration();

        List<EventType> eventList = EventType.fromStringList(Arrays.asList(e.getActions()));
        queueConfiguration.setEvents(eventList);

        Filter filter = new Filter();
        filter.setPrefixRule(e.getPrefix());
        filter.setSuffixRule(e.getSuffix());
        queueConfiguration.setFilter(filter);

        return queueConfiguration;
    }

}

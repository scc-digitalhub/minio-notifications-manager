package it.smartcommunitylab.minionotificationsmanager.model;

import java.util.Arrays;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class EventDTO {

    private long id;
    private String bucket;
    private String prefix;
    private String suffix;
    private String topic;
    private String[] actions;

    public EventDTO() {
        id = -1;
        bucket = "";
        prefix = "";
        suffix = "";
        topic = "";
        actions = new String[0];
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        if (prefix != null) {
            this.prefix = prefix;
        } else {
            this.prefix = "";
        }
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        if (suffix != null) {
            this.suffix = suffix;
        } else {
            this.suffix = "";
        }
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String[] getActions() {
        return actions;
    }

    public String[] getActions(boolean sorted) {
        if (sorted) {
            return Arrays.stream(actions).sorted().toArray(String[]::new);
        } else {
            return actions;
        }
    }

    public void setActions(String[] actions) {
        this.actions = actions;
    }

    public boolean matchesAction(String action) {
        // check for wildcard matches
        for (String a : actions) {
            // exact match
            if (a.equals(action)) {
                return true;
            }
            // wildcard match
            if (a.endsWith(":*")) {
                if (action.startsWith(a.substring(0, a.length() - 2))) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean matchesPrefix(String key) {
        if (StringUtils.isEmpty(prefix)) {
            return true;
        }

        return key.startsWith(prefix);
    }

    public boolean matchesSuffix(String key) {
        if (StringUtils.isEmpty(suffix)) {
            return true;
        }

        return key.endsWith(suffix);
    }

    @Override
    public String toString() {
        return "EventDTO [id=" + id + ", bucket=" + bucket + ", prefix=" + prefix + ", suffix=" + suffix + ", topic="
                + topic + ", actions=" + Arrays.toString(actions) + "]";
    }

    @Override
    public int hashCode() {
        // equals if they both map to same topic
        // if topic is NOT build will lead to errors
        final int prime = 31;
        int result = 1;
        result = prime * result + ((topic == null) ? 0 : topic.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        // equals if they both map to same topic
        // if topic is NOT build will lead to errors
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EventDTO other = (EventDTO) obj;
        if (topic == null) {
            if (other.topic != null)
                return false;
        } else if (!topic.equals(other.topic))
            return false;
        return true;
    }

    /*
     * Custom Methods
     */
    public boolean isValid() {
        // at least bucket and actions should not be null
        return !bucket.isEmpty() && ArrayUtils.isNotEmpty(actions);
    }

    public void buildTopic() {
        // build topic name as hash of event properties
        // by design multiple entities describing the same event will result in the same
        // topic
        StringBuilder input = new StringBuilder();
        input.append(bucket);
        // need to fetch actions as SORTED array to be consistent
        input.append(Arrays.toString(getActions(true)));
        input.append(prefix).append(suffix);

        topic = bucket + "/ev-" + DigestUtils.md5Hex(input.toString());

    }

    public Event toEvent() {
        Event event = new Event();
        event.setImported(false);
        event.setBucket(bucket);
        event.setActions(Arrays.asList(actions));
        event.setPrefix(prefix);
        event.setSuffix(suffix);

        return event;
    }

    public static EventDTO fromEvent(Event event) {
        EventDTO dto = new EventDTO();
        // copy attributes
        dto.setId(event.getId());
        dto.setBucket(event.getBucket());
        dto.setActions(event.getActions().toArray(new String[0]));
        dto.setPrefix(event.getPrefix());
        dto.setSuffix(event.getSuffix());

        // build topic
        dto.buildTopic();

        return dto;
    }

}

package it.smartcommunitylab.minionotificationsmanager.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import it.smartcommunitylab.minionotificationsmanager.model.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

    Long countByBucket(String bucket);

    List<Event> findByBucket(String bucket);

}

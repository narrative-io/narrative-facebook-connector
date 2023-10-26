package io.narrative.connectors.facebook.stores

import doobie._
import doobie.implicits._
//import doobie.postgres._
//import doobie.postgres.implicits._

//import java.util.UUID

class QueueMetricsStore extends QueueMetricsStore.QueryOps {
  override def queueSize(): doobie.ConnectionIO[Long] =
    sql"select count(*) from queue"
      .query[Long]
      .unique

  override def legacyDeliveryQueueSizePerSubscription(): doobie.ConnectionIO[Map[String, Long]] =
    sql"""
      select c.payload -> 'payload' ->> 'subscriptionId' as subscription_id, count(*)
      from queue q, commands c
      where
        c.payload ->> 'payload_type' in ('PerformDelivery') and
        (q.payload ->> 'event_revision')::integer  = c.event_revision
      group by subscription_id;
     """
      .query[(String, Long)]
      .toMap

  override def deliveryQueueSizePerConnection(): doobie.ConnectionIO[Map[String, Long]] =
    sql"""
      select c.payload -> 'payload' ->> 'connection_id' as connection_id, count(*)
      from queue q, commands c
      where
        c.payload ->> 'payload_type' in ('snapshot-appended') and
        (q.payload ->> 'event_revision')::integer  = c.event_revision
      group by connection_id;
     """
      .query[(String, Long)]
      .toMap
}

object QueueMetricsStore {
  trait QueryOps {
    def queueSize(): ConnectionIO[Long]

    def legacyDeliveryQueueSizePerSubscription(): ConnectionIO[Map[String, Long]]
    def deliveryQueueSizePerConnection(): ConnectionIO[Map[String, Long]]
  }
}

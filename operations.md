# Operations

Audience of this document:

| Role             | Objective                             |
|------------------|---------------------------------------|
| On-call Operator | Understand and react to a failure     |
| Developer        | Roll out a new version of the service |

**Table of Content**

- [Operations](#operations)
    - [Deployment](#deployment)
    - [Running the service](#running-the-service)
    - [Monitoring and Alerts](#monitoring-and-alerts)
    - [Logs](#logs)
    - [Related Processes](#related-processes)
        - [Facebook App Management](#facebook-app-management)
        - [Facebook App Review](#facebook-app-review)
    - [Troubleshooting/Failure Handling](#troubleshootingfailure-handling)
        - [Facebook Token Expiration](#facebook-token-expiration)
        - [Retrying a Delivery](#retrying-a-delivery)
    - [Known Issues](#known-issues)

## Deployment

- Bump the api and worker image versions in the appropriate [stage-specific terraform main.tf](./infra/prod/main.tf)
- Run:

```bash
assume-role sudo terraform init && assume-role sudo terraform apply
```

## Monitoring and Alerts

5** errors returned by the API and any error-level log lines generate alerts in `#auto-techops`.

This is purposefully crude and spammy until we learn more about the connector's failure modes.

Metrics like CPU and memory utilization can be viewed on the ECS service dashboards as well as via Container Insights.

- [API service dashboard](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters/facebook-connector-api-prod/services/facebook-connector-api-service-prod/health?region=us-east-1)
- [Worker service dashboard](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters/facebook-connector-worker-prod/services/facebook-connector-worker-service-prod/health?region=us-east-1)

## Logs

Logs are stored in CloudWatch.

- [API logs](https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:logs-insights$3FqueryDetail$3D$257E$2528end$257E0$257Estart$257E-3600$257EtimeType$257E$2527RELATIVE$257Eunit$257E$2527seconds$257EeditorString$257E$2527fields*20*40timestamp*2c*20*40message*0a*7c*20sort*20*40timestamp*20desc*0a*7c*20limit*20200$257EisLiveTail$257Efalse$257EqueryId$257E$2527de2ca053-551e-4b02-bc66-47960951b095$257Esource$257E$2528$257E$2527facebook-connector-api-fargate_task_log_group-prod$2529$2529)
- [Worker logs](https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:logs-insights$3FqueryDetail$3D$257E$2528end$257E0$257Estart$257E-3600$257EtimeType$257E$2527RELATIVE$257Eunit$257E$2527seconds$257EeditorString$257E$2527fields*20*40timestamp*2c*20*40message*0a*7c*20sort*20*40timestamp*20desc*0a*7c*20limit*20200$257EisLiveTail$257Efalse$257EqueryId$257E$2527de2ca053-551e-4b02-bc66-47960951b095$257Esource$257E$2528$257E$2527facebook-connector-worker-fargate_task_log_group-prod$2529$2529)

## Related Processes

### Facebook App Management

Users of the Facebook
The [Narrative Audience Uploader Facebook app](https://developers.facebook.com/apps/554425321962851/dashboard/?business_id=465873190594197).
If you are a not an app administrator and think you should be, ask to be set up as one.

- The app ID is not a secret and is available in
  SSM: [/prod/connectors/facebook/app_id](https://us-east-1.console.aws.amazon.com/systems-manager/parameters/prod/connectors/facebook/app_id/description?region=us-east-1&tab=Table#list_parameter_filters=Name:Contains:facebook)
- The app secret key is very much a secret and is also available in
  SSM: [/prod/connectors/facebook/app_secret](https://us-east-1.console.aws.amazon.com/systems-manager/parameters/prod/connectors/facebook/app_secret/description?region=us-east-1&tab=Table#list_parameter_filters=Name:Contains:facebook)
- App notifications go to `dev@narrative.io`.
- Login settings, like which domains the Facebook OAuth login flow can be rendered, are
  [on the Facebook Login settings for our application](https://developers.facebook.com/apps/554425321962851/fb-login/settings/?business_id=465873190594197)
    - **NB**: the OAuth login flow can only be tested _over https_. This means when testing the application UI locally
      you'll have to set up SSL termination at your local nginx instance. Https requirements are viral, which means
      the DSM frontend, the connector API, and Open API have to be proxied through nginx.
    - **NB**: the Facebook login only works on **localhost:9091**. You can whitelist additional ports in the login
      settings on the app management page.

### Facebook App Review

We had to undergo app review to get access to the set of API permissions we need to manage customer Ad Audiences.

This process was painful but well documented:

- [Facebook App Review 2022](https://www.notion.so/narrativeio/Facebook-App-Review-2022-a5a709155b4945eb8d67d6840973dcbd)
- [Facebook App Review 2020](https://www.notion.so/narrativeio/Facebook-App-Review-337134acfe0646389d3ff59ec137b66f)

## Troubleshooting/Failure Handling

### Facebook Token Expiration

A user's Facebook token
is [long-lived](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)
and won't expire on the basis of time, but if they do a forced log out of their account or the user who originally
granted us permissions churns out of the business that has access to the Ad Account for a delivery then we'll no longer
be able to deliver data on their behalf.

In this case there's nothing we can do but notify partner success and have the user create a new profile for their
delivery.

### Retrying a Delivery

Deliveries are retried a number of times. Afterwards we send an alert to #auto-techops with the error.
Engineering has to figure out the source of the error (e.g. token expired).

Once the root cause has been fixed, or if we conclude the failure was ephemeral we can reset the error count and the
connector will resume the delivery.

```bash
# retrying a whole delivery again
./scripts/retry.sh dev 90 

# retrying a single file
./scripts/retry.sh dev 90 part-00000-c5d00fbc-e15f-43b5-8efa-9610f749696a-c000.json
```

## Known Issues

_This section is intentionally left blank_

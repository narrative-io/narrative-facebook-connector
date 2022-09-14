# Facebook Connector

## Data Flow Diagram

The Facebook Connector uses a design equivalent to the S3 connector's for consuming events and data from the Narrative API.

See [the S3 connector README](https://github.com/narrative-io/narrative-s3-connector/blob/main/backend/readme.md) for details.

## Customer Flow

A customer's basic interaction with the connector is as follows:

1. The user chooses to set up a new profile.
2. The user goes through a Facebook OAuth flow to install the [Narrative Audience Uploader Facebook app](https://developers.facebook.com/apps/554425321962851/dashboard/?business_id=465873190594197): this gives our application permissions to manage the user's audiences.
3. The users choose an [Ad Account](https://www.facebook.com/business/help/407323696966570?id=649869995454285) to which they want audience data delivered.
4. When the user chooses to save their profile, in the backend we exchange the short-lived token received during the
   OAuth flow for a [long-lived token](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)
   that we store encrypted in the `profiles` table for the stage-appropriate `facebookconnector-db` instance.
5. The user chooses to have data delivered to the Facebook connector at checkout.
6. When data has been transacted for the user's subscription, we deliver data to the customer Ad Account as follows:
   1. If it is the first time we are delivering data for the subscription, we create a
      [new Custom Audience in the user's chosen Ad Account](https://developers.facebook.com/docs/marketing-api/reference/custom-audience/)
      using the previously stored long-lived token.
   2. We [parse the incoming data into Facebook's Audience member format](backend/worker/src/main/scala/io/narrative/connectors/facebook/AudienceParser.scala) and use the [marketing API](https://developers.facebook.com/docs/marketing-api/reference/custom-audience/#Updating).

## Facebook Resources

- [Facebook Marketing API](https://developers.facebook.com/docs/marketing-apis/)
- [Facebook API Explorer](https://developers.facebook.com/tools/explorer/)
- [Facebook Ad Account](https://www.facebook.com/business/help/407323696966570?id=649869995454285)
- [Facebook Access Tokens](https://developers.facebook.com/docs/facebook-login/guides/access-tokens)
- [Facebook Custom Audiences](https://developers.facebook.com/docs/marketing-api/audiences/guides/custom-audiences/)

### App Management

Users of the Facebook The [Narrative Audience Uploader Facebook app](https://developers.facebook.com/apps/554425321962851/dashboard/?business_id=465873190594197). If you are a not an app administrator and think you should be, ask to be set up as one.

- The app ID is not a secret and is available in SSM: [/prod/connectors/facebook/app_id](https://us-east-1.console.aws.amazon.com/systems-manager/parameters/prod/connectors/facebook/app_id/description?region=us-east-1&tab=Table#list_parameter_filters=Name:Contains:facebook)
- The app secret key is very much a secret and is also available in SSM: [/prod/connectors/facebook/app_secret](https://us-east-1.console.aws.amazon.com/systems-manager/parameters/prod/connectors/facebook/app_secret/description?region=us-east-1&tab=Table#list_parameter_filters=Name:Contains:facebook)
- App notifications go to `dev@narrative.io`.
- Login settings, like which domains the Facebook OAuth login flow can be rendered, are
  [on the Facebook Login settings for our application](https://developers.facebook.com/apps/554425321962851/fb-login/settings/?business_id=465873190594197)
  - **NB**: the OAuth login flow can only be tested _over https_. This means when testing the application UI locally
    you'll have to set up SSL termination at your local nginx instance. Https requirements are viral, which means
    the DSM frontend, the connector API, and Open API have to be proxied through nginx.
  - **NB**: the Facebook login only works on **localhost:9091**. You can whitelist additional ports in the login
    settings on the app management page.

### App Review

We had to undergo app review to get access to the set of API permissions we need to manage customer Ad Audiences.

This process was painful but well documented:

- [Facebook App Review 2022](https://www.notion.so/narrativeio/Facebook-App-Review-2022-a5a709155b4945eb8d67d6840973dcbd)
- [Facebook App Review 2020](https://www.notion.so/narrativeio/Facebook-App-Review-337134acfe0646389d3ff59ec137b66f)

## Components

### API

The [api](./api/src/main/scala) used by the Facebook Connector frontend.

### Worker

The [daemon process](./worker/src/main/scala) which consumes events and data from the Narrative API and performs the delivery of data to the customer's Facebook Audiences.


## Operations

### Alerts / Monitoring

5** errors returned by the API and any error-level log lines generate alerts in `#auto-techops`.

This is purposefully crude and spammy until we learn more about the connector's failure modes.

Metrics like CPU and memory utilization can be viewed on the ECS service dashboards as well as via Container Insights.

- [API service dashboard](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters/facebook-connector-api-prod/services/facebook-connector-api-service-prod/health?region=us-east-1)
- [Worker service dashboard](https://us-east-1.console.aws.amazon.com/ecs/v2/clusters/facebook-connector-worker-prod/services/facebook-connector-worker-service-prod/health?region=us-east-1)

### Logs

Logs are stored in CloudWatch.

- [API logs](https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:logs-insights$3FqueryDetail$3D$257E$2528end$257E0$257Estart$257E-3600$257EtimeType$257E$2527RELATIVE$257Eunit$257E$2527seconds$257EeditorString$257E$2527fields*20*40timestamp*2c*20*40message*0a*7c*20sort*20*40timestamp*20desc*0a*7c*20limit*20200$257EisLiveTail$257Efalse$257EqueryId$257E$2527de2ca053-551e-4b02-bc66-47960951b095$257Esource$257E$2528$257E$2527facebook-connector-api-fargate_task_log_group-prod$2529$2529)
- [Worker logs](https://us-east-1.console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:logs-insights$3FqueryDetail$3D$257E$2528end$257E0$257Estart$257E-3600$257EtimeType$257E$2527RELATIVE$257Eunit$257E$2527seconds$257EeditorString$257E$2527fields*20*40timestamp*2c*20*40message*0a*7c*20sort*20*40timestamp*20desc*0a*7c*20limit*20200$257EisLiveTail$257Efalse$257EqueryId$257E$2527de2ca053-551e-4b02-bc66-47960951b095$257Esource$257E$2528$257E$2527facebook-connector-worker-fargate_task_log_group-prod$2529$2529)

### Deployment

- Bump the api and worker image versions in the appropriate [stage-specific terraform main.tf](./infra/prod/main.tf)
- Run:

```bash
assume-role sudo terraform init && assume-role sudo terraform apply
```

### Troubleshooting

#### Facebook Token Expiration

A user's Facebook token is [long-lived](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)
and won't expire on the basis of time, but if they do a forced log out of their account or the user who originally
granted us permissions churns out of the business that has access to the Ad Account for a delivery then 

In this case there's nothing we can do but notify partner success and have the user create a new profile for their
delivery.

#### Retrying a Delivery

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
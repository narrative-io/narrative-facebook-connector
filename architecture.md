# Architecture

Audience of this document:

| Role                       | Objective                                                                                        |
|----------------------------|--------------------------------------------------------------------------------------------------|
| On-call Operator           | Understand and react to a failure                                                                |
| Developer                  | Build a mental model of the project in order to contribute                                       |
| Other project stakeholders | Build a mental model to reason on the priorities, understand the objectives and limitations, etc |

**Table of Content**

- [Architecture](#architecture)
    - [Background](#background)
    - [High-Level Logic and Data Flow Diagrams](#high-level-logic-and-data-flow-diagrams)
        - [Customer Flow](#customer-flow)
    - [Design Documentation](#design-documentation)
    - [Components](#components)
        - [API](#api)
        - [Worker](#worker)
    - [Architectural Deficiencies](#architectural-deficiencies)

## Background

Customers use the Facebook Connector to deliver audience segments they purchase on the marketplace to Facebook for
use in advertisements.

## High-Level Logic and Data Flow Diagrams

The Facebook Connector uses a design equivalent to the S3 connector's for consuming events and data from the Narrative
API.

See [the S3 connector architecture doc](https://github.com/narrative-io/narrative-s3-connector/blob/main/architecture.md)
for details.

### Customer Flow

A customer's basic interaction with the connector is as follows:

1. The user chooses to set up a new profile.
2. The user goes through a Facebook OAuth flow to install
   the [Narrative Audience Uploader Facebook app](https://developers.facebook.com/apps/554425321962851/dashboard/?business_id=465873190594197):
   this gives our application permissions to manage the user's audiences.
3. The users choose an [Ad Account](https://www.facebook.com/business/help/407323696966570?id=649869995454285) to which
   they want audience data delivered.
4. When the user chooses to save their profile, in the backend we exchange the short-lived token received during the
   OAuth flow for
   a [long-lived token](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)
   that we store encrypted in the `profiles` table for the stage-appropriate `facebookconnector-db` instance.
5. The user chooses to have data delivered to the Facebook connector at checkout.
6. When data has been transacted for the user's subscription, we deliver data to the customer Ad Account as follows:
    1. If it is the first time we are delivering data for the subscription, we create a
       [new Custom Audience in the user's chosen Ad Account](https://developers.facebook.com/docs/marketing-api/reference/custom-audience/)
       using the previously stored long-lived token.
    2.
   We [parse the incoming data into Facebook's Audience member format](backend/worker/src/main/scala/io/narrative/connectors/facebook/AudienceParser.scala)
   and use the [marketing API](https://developers.facebook.com/docs/marketing-api/reference/custom-audience/#Updating)
   to deliver data to Facebook.

## Design Documentation

_This section is intentionally left blank_

## Components

### API

The [api](./backend/api/src/main/scala) used by the Facebook Connector frontend.

### Worker

The [daemon process](./backend/worker/src/main/scala) which consumes events and data from the Narrative API and performs
the delivery of data to the customer's Facebook Audiences.

## Architectural Deficiencies

- We don't have any automated processes for dealing with customer token expiration: delivery failure turns into an
  internal alert that we have to pass along to Partner Success in order to get the customer to re-authorize our
  application. The tokens we use
  are [long-lived](https://developers.facebook.com/docs/facebook-login/guides/access-tokens/get-long-lived)
  and don't expire on the basis of time, so this is a pain/risk we accept for now.
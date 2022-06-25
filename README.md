# Facebook Connector

## TODO

- [ ] frontend skeleton / copypasta from s3 connector
- [ ] decide what a profile is conceptually, likely:
  - an ad account associated with a business
  - quick settings: optional custom audience id to deliver to existing audience,
      optional custom audience name
  - all associated with an oauth flow, users have some way of reauthenticating / re-generating a token for a profile
  - can we have multiple active tokens for a single user? don't think so, each new oauth flow likely expires existing
    sessions so we'll need to store tokens separately conceptually from profiles
- [ ] database, migrations
- [ ] profile store
- [ ] basic routes
- [ ] command store
- [ ] work queue
- [ ] worker to poll for new delivery events

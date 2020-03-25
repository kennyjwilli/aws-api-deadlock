# aws-api-deadlock

This repo contains a repro for a deadlock that I have hit in production. 
Essentially, if you have more than 3 `CredentialsProvider`s running `fetch` at the same time, you will hit a deadlock. 
The `CredentialProvider`s must be different objects since [this](https://github.com/cognitect-labs/aws-api/blob/aff9028757189f9875e8b3ebed18a8f07b624327/src/cognitect/aws/util.clj#L320) code serializes calls for the same provider.
We're making calls to our customers' AWS accounts.
 Parallel calls to multiple AWS accounts using different creds providers could easily happen.


## Usage

Create a `aws-info.txt` file that has the first line set to an AWS Role ARN.

```
arn:aws:iam::000000000000:role/my-role
```

Open `src/example.clj` and run `(run-many! 4)` in the REPL. 
You should get output that looks like the following.

```
2020-03-25 12:48:34.669:INFO::async-thread-macro-4: Logging initialized @8242ms to org.eclipse.jetty.util.log.StdErrLog
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 3
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 0
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 1
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 2
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:48:34 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
```

At this point, aws-api is deadlocked. 
You'll need to restart your REPL to continue. 

Running `(run-many! 3)` works as expected.

```
(run-many! 3)
=> nil
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 0
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 1
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke 2
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :tag :fetch-creds :role-arn arn:aws:iam::000000000000:role/my-role
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: Unable to fetch credentials from environment variables.
Mar 25, 2020 12:49:24 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: Unable to fetch credentials from system properties.
Mar 25, 2020 12:49:25 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke-done 1
Mar 25, 2020 12:49:25 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke-done 0
Mar 25, 2020 12:49:25 PM clojure.tools.logging$eval1715$fn__1718 invoke
INFO: :invoke-done 2
```
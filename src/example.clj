(ns example
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.core.async :as async]
    [cognitect.aws.client.api :as aws]
    [cognitect.aws.credentials :as aws-creds])
  (:import (java.util Date)
           (java.lang ProcessHandle)))

(def aws-info-file "aws-info.txt")
(def aws-info
  (->> aws-info-file
       (slurp)
       (str/split-lines)
       (zipmap [:role-arn])))

(def *sts-client
  (delay (aws/client {:api :sts})))

(defn fetch-creds
  ([role-arn] (fetch-creds @*sts-client role-arn))
  ([sts-client role-arn]
   (when-let [creds (:Credentials
                      (aws/invoke
                        sts-client {:op      :AssumeRole
                                    :request {:RoleArn         role-arn
                                              :RoleSessionName (str "session-" (inst-ms (Date.)))}}))]
     {:aws/access-key-id     (:AccessKeyId creds)
      :aws/secret-access-key (:SecretAccessKey creds)
      :aws/session-token     (:SessionToken creds)
      ::aws-creds/ttl        (aws-creds/calculate-ttl creds)})))

(defn cached-role-arn-creds-provider
  [role-arn]
  (aws-creds/cached-credentials-with-auto-refresh
    (reify
      aws-creds/CredentialsProvider
      (fetch [_]
        (log/info :tag :fetch-creds
                  :role-arn role-arn)
        (fetch-creds role-arn)))))

(defn client
  []
  (aws/client {:api                  :ec2
               :credentials-provider (cached-role-arn-creds-provider (:role-arn aws-info))}))

(defn example-invoke
  [c]
  (aws/invoke c {:op :DescribeInstances}))

(comment
  (def c (client))
  (example-invoke c)
  )

(defn run-many!
  [n]
  (async/merge
    (map (fn [n]
           (async/thread
             (let [c (client)]
               (log/info :invoke n)
               (example-invoke c)
               (log/info :invoke-done n))))
         (range n))))

(comment
  (run-many! 4)
  (run-many! 3)
  )

(defn -main
  [& args]
  (let [n (Long/parseLong (first args))
        pid (.pid (ProcessHandle/current))]
    (log/info :n-threads n :pid pid)
    (async/<!! (run-many! n))))
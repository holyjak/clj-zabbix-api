(ns clj-zabbix-api.core
  (:require [clj-http.client :as http]))

(declare post-raw)

(def ZABBIX-URL "https://zabbix.example.com/api_jsonrpc.php")

(defn login [{:keys [url user password]}]
       "Authorize with Zabbix, return a map keeping the auth. token.
        Ex.: (login \"https://zabbix.example.com/api_jsonrpc.php\" \"me@here\" \"secret\")"

       (let [auth-token (post-raw {:url url
             :user user
             :password password
             :method "user.authenticate"
             :params {:user user :password password}
              })]
         ;; Derive from post-raw by binding the known parameters
         (do
           (println "Authenticated w/ Zabbix:" auth-token)
		         {:post (fn post-call [method params]
		                  (post-raw {:url url
		                             :user user
		                             :password password
		                             :auth auth-token
		                             :method method
		                             :params params
		                             }))})))

(defn trigger-get [zbx group name]
  "Get triggers with the given name for hosts in the given host group.
  Ex.: (trigger-get zbx \"Analytics production\" \"Processor load is too high on {HOSTNAME}\")"
  (let [req {:output "shorten",
                     :group group, ;; filter by host group name
                     "select_hosts" "extend" ;; include host names
                     :filter {:description name}}
        triggers ((:post zbx) "trigger.get" req)]
    ;; Extract only the info needed: id, status (enabled?), host dns
    ;; (there can be multiple hosts but in our case there is always only 1)
    (map (fn [t]
           (let [host (first (:hosts t))]
             {:triggerid (:triggerid t)
              :status (java.lang.Integer/parseInt (:status host))
              :dns (:dns host)}))
         triggers))) ;; :hosts :hostid


(def DISABLED "Trigger disabled status" 1)
(def ENABLED "Trigger enabled status" 0)

(defn trigger-update [zbx triggerid enable]
  ;; trigger.update is broken, fixed in Zabbix 1.8.4 https://support.zabbix.com/browse/ZBX-3267
  ((:post zbx) "trigger.update" {:triggerid triggerid :status (if enable ENABLED DISABLED)}))

(defn- post-raw [{:keys [url user password method params auth] :or {:auth nil}}]
  "Post a call to Zabbix, returning the response result JSON as a Clojure map.
  :params is a Clojure map with keys"
  ;; Note: In our setup, basuc-auth is required for access to the
  ;; Zabbix server (otherwise 401) while user.authenticate is checked by
  ;; Zabbix itself (=> Invalid params - Not authorized if no auth. token)
  (let
      [response (http/post ZABBIX-URL
                           {:basic-auth [user password]
                            :form-params {
                                          :jsonrpc "2.0" :id 1
                                          :auth auth
                                          :method method
                                          :params params
                                          }
                            :content-type :json
                            :as :json})
       body (:body response)]
    (if (:error body)
      (do
        (println "RESPONSE: " body)
        (throw (Exception. (str "Error executing " method ": " (:error body) ". Params: " params))))
      (:result body))))

(defn disable-triggers [zbx]
  "Do it all: disable a particular trigger on all data nodes in a group (on not others)"
  (let [triggers (trigger-get zbx "Analytics production" "Processor load is too high on {HOSTNAME}")
        triggers2disable (filter
                        #(and
                           (.contains (:dns %) "-data")
                           (= ENABLED (:status %)))
                              triggers)]
    (do
      (doseq [t triggers2disable]
        (trigger-update zbx (:triggerid t) false))
      (map :dns triggers2disable))))

;; For testing:
;; (def zbx (login {
;;                  :url "https://zabbix.example.com/api_jsonrpc.php"
;;                  :user "me"
;;                  :password "secret"}))

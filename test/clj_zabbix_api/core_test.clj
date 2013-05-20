(ns clj-zabbix-api.core-test
  (:require [clojure.test :refer :all]
            [clj-zabbix-api.core :refer :all]
            [clj-http.fake :refer :all]))

;; See https://github.com/myfreeweb/clj-http-fake#readme

(def ZBXURL "http://zabbixtest.example.com")

(deftest login-binds-known-arguments
  ;; Call login, store the resulting accessor for zabbix, invoke it & check result
  (is (= (let [zbx (with-redefs [post-raw (fn [_] "my-dummy-auth-token")]
                     (login {:url ZBXURL, :user "usr", :password "psw" }))]
           (with-redefs [post-raw (fn [req] req)]
            ((:post zbx) "dummy.operation" {:myparam :myvalue})))
         ;; When posted to Zabbix, there should be the action to invoke, custom params, auth. headers
         ;; (the fake http impl. will return the request we have sent)
         {:url ZBXURL
          :user "usr"
          :password "psw"
          :auth "my-dummy-auth-token"
          :method "dummy.operation"
          :params {:myparam :myvalue}})))

(run-tests)

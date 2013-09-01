(ns server.core
  (:require [taoensso.carmine :as car :refer (wcar)]
            [twilio.core :as twilio])
  (:use [compojure.route :only [files not-found files]]
      [compojure.handler :only [site]] ; form, query params decode; cookie; session, etc
      [compojure.core :only [defroutes GET POST DELETE ANY context]]
      [org.httpkit.server :only [run-server]]
      [pusher]
      [server.helpers]
      [clojure.string :only (split triml lower-case)]
      [cheshire.core :as json]))

(def redis-uri "redis://redistogo:44097e3f7131e73fcd47fdc028a63f88@jack.redistogo.com:9443/")

(def twilio-sid "AC04e734ab7fa67ec0a4974c1b1c1f7151")
(def twilio-token "8ed901fcb66a4ec846b6444353cb0897")
(def twilio-caller "+441782454803")

(defn send-sms [msg]
  (twilio/send-message (assoc msg :From twilio-caller) twilio-sid twilio-token))

(defn twilio-call [call]
  (twilio/make-call (assoc call :Url "http://drone.whisk.co.uk/twilio-call-descriptor.xml" :From twilio-caller) twilio-sid twilio-token))

(defn push [action]
  (with-pusher-auth ["53075" "c84dcb73872041ad435f" "e50f67b2699e4d34af97"]
  (with-pusher-channel "test_channel"
    (println (str "push to channel: " action))
    (trigger action {:message action})))
  action)

(def server-conn {:pool {}
                  :spec {:uri redis-uri}})

(defmacro wcar* [& body] `(car/wcar server-conn ~@body))

(defn pushr [action]
  (wcar* (car/publish "channel" action))
  (println action)
  action)

(defn enrich-action [msg]
  (case (msg :action)
    "forward" (assoc msg :distance 1)
    "clockwise" (assoc msg :rotation 135)
    msg))

(defn process-plan [plan]
  (let [words (split (triml (lower-case plan)) #"\s+")
        msg (wrap-msg {:action "plan" :steps words})]
    (pushr msg)))

(defn process-event [event]
  ;some handlig stuff
  (let [base {:action (lower-case event)}
        msg (wrap-msg (enrich-action base))]
    (pushr msg)))

(defn success [action]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    action})

(defn welcome [req]
  (success "ok"))

(defn execute-action [req]
  (let [action (-> req :params :action)]
    (success (process-event action))))

(defn execute-plan [req]
  (let [body (json/parse-stream (clojure.java.io/reader (req :body)))
        msg (wrap-msg (assoc body :action "plan"))]
    (pushr msg)
    (success msg)))

(defn incoming-message [req]
  (let [from (-> req :params :From)
        body (-> req :params :Body)]
    (case (triml (lower-case body))
      "obtain" (do
                 (twilio-call {:To from})
                 (success "ok"))
      (do (process-plan body)
          (send-sms {:To "+447807488602" :Body "Hey! I'm Whisk's drone"})
          (success "ok")))))

(defn handle-call [req]
  (println (-> req :params))
  (success "ok"))

(defn twilio-call-descriptor [req]
  {:status  200
   :headers {"Content-Type" "application/xml"}
   :body    (twilio/xml-descriptor)})

(defn process-digits [req]
  (let [digits (-> req :params :Digits)
        body (twilio/handle-digits digits process-event)]
    {:status  200
     :headers {"Content-Type" "application/xml"}
     :body    body}))

(defn make-call [req]
  (twilio-call {:To "+447807488602"})
  (success "ok"))

(defroutes all-routes
  (GET "/" [] welcome)
  (GET "/action/:action" [] execute-action)
  (POST "/plan" [] execute-plan)
  (POST "/twilio/inbox" [] incoming-message)
  (POST "/twilio/call" [] handle-call)
  (POST "/twilio-call-descriptor.xml" [] twilio-call-descriptor)
  (GET "/twilio-call-descriptor.xml" [] twilio-call-descriptor)
  (GET "/process-digits" [] process-digits)
  (POST "/process-digits" [] process-digits)
  (GET "/make-call" [] make-call)
  (files "/static/"))

(defn -main [& args]
  (run-server (site #'all-routes) {:port (read-string (or (first args) "8080"))})
  (println "server started"))
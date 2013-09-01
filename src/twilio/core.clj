(ns twilio.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json]
            [clojure.data.xml :as xml]))

(def base "https://api.twilio.com/2010-04-01")

(defn make-request-url [endpoint sid]
  (format
    "%s/Accounts/%s/%s.json"
    base
    sid
    endpoint ))

(defn trequest
  "Make a generic HTTP request"
  [method url sid token & params]
  (try
    (let [f (condp = method
              :post client/post
              :else client/get)]
    (f url
      {:accept :json
       :form-params (first params)
       :basic-auth [sid token]}))
  (catch Exception e
     (let [exception-info (.getData e)]
     (println exception-info)
     (select-keys
       (into {} (map (fn [[k v]] [(keyword k) v])
         (json/parse-string
             (get-in exception-info [:object :body]))))
             (vector :status :message :code))))))

(defn send-message
  "Send an SMS message
    msg is a map with the following keys
    - From
    - To
    - Body
   Msg, sid and token are mandatory params.
  "
  [msg sid token]
  (let [url (make-request-url "SMS/Messages" sid)]
    (trequest :post url sid token msg)))

(defn make-call
  "Call to number
    call is a map with the following mandatory keys:
    - From
    - To
    - Url or ApplicationSid
    Visit https://www.twilio.com/docs/api/rest/making-calls to see some optional keys for call map.
    Call, sid and token are mandatory params.
  "
  [call sid token]
  (let [url (make-request-url "Calls" sid)
        resp (trequest :post url sid token call)
        body (json/parse-string (:body resp) true)]
    body))


(defn xml-descriptor []
  (xml/emit-str
    (xml/element :Response {}
                 (xml/element :Gather {:action "/process-digits" :method "GET"}
                          (xml/element :Say {} "Please input your command for drone")))))

(defn convert-digit
  [digit]
  (case digit
    \0 "land"
    \2 "takeoff"
    \4 "left"
    \5 "forward"
    \6 "right"
    \8 "backwards"
    "None found"))

(defn handle-digits [digits func]
  (let [digit (last digits)
        action (convert-digit digit)]
    (func action)
      (xml/emit-str
      (xml/element :Response {}
                 (xml/element :Gather {:action "/process-digits" :method "GET"}
                          (xml/element :Say {} (str "Action " action ". Please input your command for drone")))                 ))))

(defn parse-digits
  [digits]
  (for [digit digits] (convert-digit digit)))

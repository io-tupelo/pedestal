;   Copyright (c) Alan Thompson. All rights reserved.  
;   The use and distribution terms for this software are covered by the Eclipse Public
;   License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the
;   file epl-v10.html at the root of this distribution.  By using this software in any
;   fashion, you are agreeing to be bound by the terms of this license.
;   You must not remove this notice, or any other, from this software.
(ns tupelo.pedestal
  "Utils for Pedestal"
  (:use tupelo.core)
  (:require
    [clojure.set :as set]
    [io.pedestal.http :as http]
   ;[io.pedestal.http.route :as route]
    [io.pedestal.interceptor :as interceptor]
    [io.pedestal.interceptor.chain :as chain]
    [io.pedestal.test :as pedtst]
    [schema.core :as s]
    [tupelo.schema :as tsk]
    [tupelo.pedestal.headers :as hdrs]
    ))

;-----------------------------------------------------------------------------
; Plumatic Schema type definitions

 (def context-keys-base #{:bindings
                          :io.pedestal.interceptor.chain/execution-id
                          ; :io.pedestal.interceptor.chain/queue  ;   ; NOT ALWAYS PRESENT!
                          :io.pedestal.interceptor.chain/stack
                          :io.pedestal.interceptor.chain/terminators
                          :request
                          :servlet
                          :servlet-config
                          :servlet-request
                          :servlet-response})

 (def request-keys-base #{:async-supported?
                          :body
                          :headers
                          :path-info
                          :protocol
                          :query-string
                          :remote-addr
                          :request-method
                          :scheme
                          :server-name
                          :server-port
                          :uri})

 (def Request
   "Plumatic Schema definition of a Pedestal Request"
   {:async-supported? s/Bool
    :body             s/Any
    :headers          {s/Str s/Str}
    :path-info        (s/maybe s/Str)
    :protocol         s/Str
    :query-string     (s/maybe s/Str)
    :remote-addr      s/Str
    :request-method   s/Keyword
    :scheme           (s/maybe s/Keyword)
    :server-name      (s/maybe s/Str)
    :server-port      s/Int
    :uri              (s/maybe s/Str)
    s/Keyword         s/Any})

 (def Context
   "Plumatic Schema definition of a Pedestal Context"
   {:bindings                                   tsk/Map
    :io.pedestal.interceptor.chain/execution-id s/Int
    ; :io.pedestal.interceptor.chain/queue      [s/Any]  ; NOT ALWAYS PRESENT!
    :io.pedestal.interceptor.chain/stack        [tsk/KeyMap]
    :io.pedestal.interceptor.chain/terminators  [s/Any]
    :request                                    Request
    :servlet                                    s/Any
    :servlet-config                             s/Any
    :servlet-request                            s/Any
    :servlet-response                           s/Any
    s/Keyword                                   s/Any})

 (def TableRouteInfo
   "Plumatic Schema definition of a Pedestal TableRouteInfo"
   {:verb                          s/Keyword
    :path                          s/Str
    :route-name                    s/Keyword
    (s/optional-key :interceptors) s/Any
    (s/optional-key :constraints)  s/Any})

 (s/defn table-route :- tsk/Tuple
   "Creates a Pedestal table-route entry from a context map."
   [route-map :- TableRouteInfo]
   (prepend
     (grab :path route-map)
     (grab :verb route-map)
     (grab :interceptors route-map)
     (keyvals-seq {:missing-ok true
                   :the-map    route-map :the-keys [:route-name :constraints]})))

 (s/defn context? :- s/Bool
   "Returns true if the map arg is a Pedestal context map"
   [map-in :- tsk/KeyMap]
   (let [keys-found (set (keys map-in))]
     (set/subset? context-keys-base keys-found)))

 (s/defn request? :- s/Bool
   "Returns true if the map arg is a Pedestal request map"
   [map-in :- tsk/KeyMap]
   (let [keys-found (set (keys map-in))]
     (set/subset? request-keys-base keys-found)))

 (s/defn interceptor? :- s/Bool
   "Returns true if the map arg is a Pedestal interceptor map"
   [map-in :- tsk/KeyMap]
   (let [enter-fn   (get map-in :enter)
         leave-fn   (get map-in :leave)
         error-fn   (get map-in :error)
         keys-found (keys map-in)]
     (and
       (or (not-nil? enter-fn) (not-nil? leave-fn) (not-nil? error-fn))
       (set/subset? keys-found #{:name :enter :leave :error}))))


;---------------------------------------------------------------------------------------------------
(def ctx-trim-enable
  "Flag to control automatic trimming of interceptor-chain control information."
  (atom true))
(s/defn ctx-trim :- tsk/KeyMap
  [ctx :- tsk/KeyMap] ; #todo need test
  "Removes seldom-used keys from interceptor-chain context map to declutter debug printouts
  (:queue, :stack, :terminators)."
  (if @ctx-trim-enable
    (dissoc ctx
      :io.pedestal.interceptor.chain/queue  ; NOT ALWAYS PRESENT!
      :io.pedestal.interceptor.chain/stack
      :io.pedestal.interceptor.chain/terminators )
    ctx))

;---------------------------------------------------------------------------------------------------
(defn ^:no-doc definterceptor-impl
   [name ctx]
   (assert symbol? name)
   (assert map? ctx)
   (let [keys-found (set (keys ctx))
         >>         (when-not (set/subset? keys-found #{:enter :leave :error})
                      (throw (IllegalArgumentException. (str "invalid keys-found:  " keys-found))))
         enter-fn   (get ctx :enter)
         leave-fn   (get ctx :leave)
         error-fn   (get ctx :error)
         >>         (when-not (or enter-fn leave-fn)
                      (throw (IllegalArgumentException. "Must have 1 or more of [enter-fn leave-fn error-fn]")))
         intc-map   (glue {:name (keyword name)}
                      (if (not-nil? enter-fn)
                        {:enter (grab :enter ctx)}
                        {})
                      (if (not-nil? leave-fn)
                        {:leave (grab :leave ctx)}
                        {})
                      (if (not-nil? error-fn)
                        {:error (grab :error ctx)}
                        {}))]
     `(def ~name
        ~intc-map)))

 (defmacro definterceptor
   "Creates a Pedestal interceptor given a name and a map like
   (definterceptor my-intc
     { :enter  <enter-fn>
       :leave  <leave-fn>
       :error  <error-fn> } )  "
   [name ctx]
   (definterceptor-impl name ctx))


;---------------------------------------------------------------------------------------------------

(def default-service-map
  "Default options for configuring a Pedestal service"
  {::http/type          :jetty
   ::http/port          8890 ; default port
   ::http/host          "0.0.0.0" ; *** CRITICAL ***  to make server listen on ALL IP addrs not just `localhost`
   ::http/join?         true ; true => block the starting thread (want this for supervisord in prod); false => don't block
   ::http/resource-path "public" ; => use "resources/public" as base (see also ::http/file-path)
   ::http/routes        nil ; <= user-supplied
   })

(def ^:no-doc service-state
  "Holds onto the Pedestal Service state returned by io.pedestal.http/create-server"
  (atom nil))

(defn service-fn
  "Returns the `service-function` (which can be used for testing via pedestal.test/response-for)"
  []
  (grab ::http/service-fn @service-state))

(defn server-start!
  "Starts the Jetty HTTP server for a Pedestal service as configured via `define-service`"
  []
  (http/start @service-state))

(defn server-stop!
  "Stops the Jetty HTTP server."
  []
  (http/stop @service-state))

(defn server-restart!
  "Stops and restarts the Jetty HTTP server for a Pedestal service"
  []
  (server-stop!)
  (server-start!))

(s/defn server-config!
  ([] (server-config! {}))
  ([server-opts :- tsk/KeyMap]
    (let [opts-to-use (glue default-service-map server-opts)]
      (reset! service-state (http/create-server opts-to-use)))))

(defmacro with-service
  "Run the forms in the context of a Pedestal service definition"
  [service-map & forms]
  `(let [opts-to-use# (glue default-service-map ~service-map)]
     (reset! service-state (http/create-server opts-to-use#))
     (try
       ~@forms
       (finally
         (reset! service-state nil)))))

(defmacro with-server
  "Start & stop the server, even if exception occurs."
  [service-map & forms]
  `(with-service ~service-map
     (try
       (server-start!) ; sends log output to stdout
       ~@forms
       (finally
         (server-stop!)))))

(s/defn invoke-interceptors
  "Given a context and a vector of interceptor-maps, invokes the interceptor chain
  and returns the resulting output context."
  [ctx :- tsk/KeyMap
   interceptors :- [tsk/KeyMap]] ; #todo => specialize to interceptor maps
  (let [pedestal-interceptors (mapv interceptor/map->Interceptor interceptors)]
    (chain/execute ctx pedestal-interceptors)))

(defn service-get
  "Given that a Pedestal service has been defined, return the response for an HTTP GET request.
  Does not require a running Jetty server."
  [& args]
 ;(spyx :tupelo.pedestal/service-get args)
  (let [full-args (prepend (service-fn) :get args)]
   ;(spyx :tupelo.pedestal/service-get full-args)
    (apply pedtst/response-for full-args)))

(defn service-post
  "Given that a Pedestal service has been defined, return the response for an HTTP POST request.
  Does not require a running Jetty server."
  [& args]
  (let [full-args (prepend (service-fn) :post args)]
    (apply pedtst/response-for full-args)))

(defn service-delete
  "Given that a Pedestal service has been defined, return the response for an HTTP DELETE request.
  Does not require a running Jetty server."
  [& args]
  (let [full-args (prepend (service-fn) :delete args)]
    (apply pedtst/response-for full-args)))

















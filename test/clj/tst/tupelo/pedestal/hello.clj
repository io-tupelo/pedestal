(ns tst.tupelo.pedestal.hello
  (:use tupelo.core tupelo.test)
  (:require
    [clojure.edn :as edn]
    [io.pedestal.http :as http]
    [io.pedestal.http.route :as route]
    [io.pedestal.http.route.definition.table :as table]
    [io.pedestal.test :as ptst]
    [org.httpkit.client :as http-client]
    [tupelo.pedestal :as tp]
    [tupelo.pedestal.interceptors :as tpi]
    [tupelo.string :as str]
    ))

(defn greeting-for
  "Returns a greeting for `user-name` if present, else a generic greeting. "
  [user-name]
  (let [user-name (str/trim (str user-name))] ; nil => ""
    (cond
      (str/blank? user-name) "Hello, World! \n"
      (str/lowercase= user-name "Voldemort") ::unmentionable
      :else (format "Hello, %s!" user-name))))

(tp/definterceptor respond-hello-intc
  {:leave (fn [ctx]
            (let [user-name (get-in ctx [:request :query-params :name])
                  body-str  (greeting-for user-name)
                  response  (if (= ::unmentionable body-str)
                              (tp/not-found)
                              (tp/ok body-str))]
              (assoc ctx :response response)))})

(tp/definterceptor catch-all-intc
  {:leave (fn [ctx] (glue ctx {:response (tp/ok "Catch-all")}))})

(def routes
  (route/expand-routes
    #{;  vvv  wildcard doesn't work using route/expand-routes
      ; (tp/table-route {:verb :get :path "/*other" :route-name :catch-all :interceptors [catch-all-intc]})
      (tp/table-route {:verb :get :path "/echo" :route-name :get-echo :interceptors [tpi/echo-interceptor]})
      (tp/table-route {:verb :get :path "/hello" :route-name :get-hello :interceptors [respond-hello-intc]})}))

(def base-service-map
  {::http/routes routes
   ::http/port   8890
   ::http/join?  false}) ; don't block the starting thread for tests

;---------------------------------------------------------------------------------------------------
(def tst-service-map
  (glue base-service-map {::http/join? false}))

(dotest
  (println "tst/main - enter")
  ; capture jetty logging from System/err
   (let [sys-err-str (with-system-err-str
                       (tp/with-server tst-service-map ; test over http using jetty server
                         (let [resp @(http-client/get "http://localhost:8890/hello")]
                           (is-nonblank= (grab :body resp) "Hello, World!")
                           (is= 200 (grab :status resp))
                           (comment ; sample response below:
                             {:body    "Hello, World! \n",
                              :headers {:content-security-policy           "object-src 'none'; script-src 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:;",
                                        :content-type                      "text/plain",
                                        :date                              "Fri, 10 Sep 2021 23:00:38 GMT",
                                        :strict-transport-security         "max-age=31536000; includeSubdomains",
                                        :transfer-encoding                 "chunked",
                                        :x-content-type-options            "nosniff",
                                        :x-download-options                "noopen",
                                        :x-frame-options                   "DENY",
                                        :x-permitted-cross-domain-policies "none",
                                        :x-xss-protection                  "1; mode=block"},
                              :opts    {:method :get, :url "http://localhost:8890/hello"},
                              :status  200}))))]
     (is= "" sys-err-str)
     ; (is (not-empty? (str/fgrep "GET /greet" sys-err-str)))
     ;    eg '[qtp1379526008-32] INFO io.pedestal.http - {:msg "GET /greet", :line 80}'
     ; sometimes blank
     )
  (println "tst/main - exit")
  )

(dotest
  (discarding-system-err
    (tp/with-service tst-service-map ; mock testing w/o actually starting jetty
      (let [resp (tp/service-get "/echo")
            body (edn/read-string (grab :body resp))
            ]
        (is= (grab :status resp) 200)
        (is (submatch?
              {:request  {:body           ""
                          :headers        {"content-length" "0" "content-type" ""}
                          :path-info      "/echo"
                          :path-params    []
                          :protocol       "HTTP/1.1"
                          :remote-addr    "127.0.0.1"
                          :request-method :get
                          :uri            "/echo"}
               :response nil
               :route    {:interceptors [{:name :echo-interceptor}]
                          :method       :get
                          :path         "/echo"
                          :path-params  []
                          :path-parts   ["echo"]
                          :route-name   :get-echo}}
              (tp/walk-ctx-trim body)))))))

(dotest
  (discarding-system-err
    (tp/with-service tst-service-map ; mock testing w/o actually starting jetty
      (let [resp (tp/service-get "/hello?name=Fred")
            body (grab :body resp)
            ]
        (is= (grab :status resp) 200)
        (is= body "Hello, Fred!")))))



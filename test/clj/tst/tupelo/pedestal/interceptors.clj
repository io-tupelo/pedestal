(ns tst.tupelo.pedestal.interceptors
  (:use tupelo.core tupelo.test)
  (:require
    [clojure.edn :as edn]
    [io.pedestal.http :as http]
    [io.pedestal.http.route :as route]
    [io.pedestal.test :as ptst]
    [tupelo.pedestal :as tp]
    [tupelo.pedestal.interceptors :as tpi]
    ))

;---------------------------------------------------------------------------------------------------
(def routes
  (route/expand-routes
    #{
      (tp/table-route {:verb :get :path "/echo" :route-name :get-echo :interceptors [tpi/echo-interceptor]})
      }))

(def tst-service-map
  {::http/routes routes
   ::http/join?  false}) ; true => block the starting thread (want this for supervisord in prod); false => don't block

(dotest
  (discarding-system-err
    ; ^^^ use this to discard Pedestal info msgs like:
    ;         [main] INFO io.pedestal.http - {:msg "POST /records", :line 80}

    (tp/with-service tst-service-map ; mock testing w/o actually starting jetty
      (let [resp (ptst/response-for (tp/service-fn) :get "/echo")
            body (edn/read-string (grab :body resp))
            body-trimmed  (tp/walk-ctx-trim body)
            expected  {:async?           :tupelo.parse/edn-non-parsible
                       :bindings         #:tupelo.parse{:edn-non-parsible :tupelo.parse/edn-non-parsible}
                       :enter-async      [:tupelo.parse/edn-non-parsible]
                       :request          {:async-supported?   true
                                          :body               ""
                                          :character-encoding "UTF-8"
                                          :content-length     0
                                          :content-type       ""
                                          :context-path       ""
                                          :headers            {"content-length" "0" "content-type" ""}
                                          :path-info          "/echo"
                                          :path-params        []
                                          :protocol           "HTTP/1.1"
                                          :query-string       nil
                                          :remote-addr        "127.0.0.1"
                                          :request-method     :get
                                          :scheme             nil
                                          :server-name        nil
                                          :server-port        -1
                                          :servlet            :tupelo.parse/edn-non-parsible
                                          :servlet-request    :tupelo.parse/edn-non-parsible
                                          :servlet-response   :tupelo.parse/edn-non-parsible
                                          :uri                "/echo"
                                          :url-for            :tupelo.parse/edn-non-parsible}
                       :response         nil
                       :route            {:interceptors [{:enter nil
                                                          :error nil
                                                          :leave :tupelo.parse/edn-non-parsible
                                                          :name  :echo-interceptor}]
                                          :method       :get
                                          :path         "/echo"
                                          :path-params  []
                                          :path-parts   ["echo"]
                                          :path-re      :tupelo.parse/edn-non-parsible
                                          :route-name   :get-echo
                                          :io.pedestal.http.route.prefix-tree/satisfies-constraints?
                                                        :tupelo.parse/edn-non-parsible}
                       :servlet          :tupelo.parse/edn-non-parsible
                       :servlet-config   nil
                       :servlet-request  :tupelo.parse/edn-non-parsible
                       :servlet-response :tupelo.parse/edn-non-parsible
                       :url-for          :tupelo.parse/edn-non-parsible}
            ]
        (is= (grab :status resp) 200)
        (is= body-trimmed expected)
        ))))


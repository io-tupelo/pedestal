;   Copyright (c) Alan Thompson. All rights reserved.
;   The use and distribution terms for this software are covered by the Eclipse Public
;   License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the
;   file epl-v10.html at the root of this distribution.  By using this software in any
;   fashion, you are agreeing to be bound by the terms of this license.
;   You must not remove this notice, or any other, from this software.
(ns tupelo.pedestal.headers
  (:use tupelo.core))

; #todo write http-headers->text and http-headers->edn to convert keys
(def html-headers-edn
  {:accept                            "Accept"
   :application-edn                   "application/edn"
   :application-json                  "application/json"
   :application-xml                   "application/xml"
   :content-security-policy           "Content-Security-Policy"
   :content-type                      "Content-Type"
   :location                          "Location"
   :strict-transport-security         "Strict-Transport-Security"
   :text-html                         "text/html"
   :text-javascript                   "text/javascript"
   :text-plain                        "text/plain"
   :x-content-type-options            "X-Content-Type-Options"
   :x-download-options                "X-Download-Options"
   :x-frame-options                   "X-Frame-Options"
   :x-permitted-cross-domain-policies "X-Permitted-Cross-Domain-Policies"
   :x-xss-protection                  "X-XSS-Protection"} )

;-----------------------------------------------------------------------------
(defmacro defheader
  [name]
  `(def ~name ~(grab (keyword name) html-headers-edn)))

; http string constants
(defheader accept)
(defheader application-edn)
(defheader application-json)
(defheader application-xml)
(defheader content-security-policy)
(defheader content-type)
(defheader location)
(defheader strict-transport-security)
(defheader text-html)
(defheader text-javascript)
(defheader text-plain)
(defheader x-content-type-options)
(defheader x-download-options)
(defheader x-frame-options)
(defheader x-permitted-cross-domain-policies)
(defheader x-xss-protection)


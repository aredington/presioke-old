(ns presioke.test.behavior
  (:require [io.pedestal.app :as app]
            [io.pedestal.app.protocols :as p]
            [io.pedestal.app.tree :as tree]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.render :as render]
            [io.pedestal.app.util.test :as test])
  (:use clojure.test
        presioke.behavior
        [io.pedestal.app.query :only [q]]))

;; Test a transform functio
(defn volatile-inc [n]
        (if (< n 128)
          (inc n)
          (throw (ex-info "128 deep" {}))))
(defn volatile-seq []
  (iterate volatile-inc 1))

(defn loud-cycle
  "Returns a lazy (infinite!) sequence of repetitions of the items in coll."
  [coll]
  (println "\n-- cycling -------------------------------------")
  (println  (interpose "\n" (map str  (.getStackTrace  (Thread/currentThread)))))
  (println "\n-- done cycle ----------------------------------")
  (lazy-seq
      (when-let [s (seq coll)]
        (concat s (loud-cycle s)))))

(def loud-pres
  "A canned seq of 5 funny images for testing the UI against."
  (loud-cycle ["http://www.ihasaflavor.com/lolcats/i-has-a-bucket.jpg"
          "http://2.bp.blogspot.com/-SnTjGcu-_PM/UVyG1J3kkEI/AAAAAAAAAIE/RxVWpc2UuQE/s1600/ceiling_cat.jpg"
          "http://cdn5.benzinga.com/files/danger_zone_-_kenny_loggins_2.jpg"
          "http://www.bhmpics.com/walls/success_kid-other.jpg"
          "http://2.media.collegehumor.cvcdn.com/63/62/9c424ebbf9daa3cdef16dd7368e1eaa7-courage-wolf.jpg"]))

(comment
  ;; Strangely enough, volatile-seq DOES NOT run infinitely, but
  ;; loud-pres does
  (def dummy-image-spigot volatile-seq))

(def dummy-image-spigot loud-pres)
(deftest test-example-transform
  (is (= (example-transform {} {msg/type msg/init, :value dummy-image-spigot})
         dummy-image-spigot)))

;; Build an application, send a message to a transform and check the transform
;; state

(deftest test-app-state
  (let [app (app/build example-app)]
    (app/begin app)
    (is (vector?
         (test/run-sync! app [{msg/topic :example-transform, msg/type msg/init, :value dummy-image-spigot}])))
    (is (= (-> app :state deref :models :example-transform) dummy-image-spigot))))

;; Use io.pedestal.app.query to query the current application model

(deftest test-query-ui
  (let [app (app/build example-app)
        app-model (render/consume-app-model app (constantly nil))]
    (app/begin app)
    (is (test/run-sync! app [{msg/topic :example-transform, msg/type msg/init, :value "x"}]))
    (is (= (q '[:find ?v
                :where
                [?n :t/path [:io.pedestal.app/view-example-transform]]
                [?n :t/value ?v]]
              @app-model)
           [["x"]]))))

(deftest test-shuffler-combinator
  (let [seqs ['(:a :b :c) '(:e :f :g) '(:h :i :j)]
        first-shuffle-combination (apply shuffle-combine seqs)
        second-shuffle-combination (apply shuffle-combine seqs)
        naive-merged-seqs (apply concat seqs)]
    (is (= (set naive-merged-seqs) (set first-shuffle-combination) (set second-shuffle-combination))
        "Shuffle combinator slurps all the data from input seqs")
    (is (not (= first-shuffle-combination second-shuffle-combination))
        "The shuffled seqs are differently ordered")))

(defn uri?
  [string]
  (re-matches #"http://[^/]/.*" string))

(deftest test-uri-spigot
  (for [source spigot-sources]
    (let [uri-seq (uri-spigot source)]
      (is (every? uri? (take 10 uri-seq))
          (str "uri-spigot for " source " returns a seq of uris")))))

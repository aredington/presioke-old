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

;; Test a transform function

(deftest test-example-transform
  (is (= (example-transform {} {msg/type msg/init, :value "x"})
         "x")))

;; Build an application, send a message to a transform and check the transform
;; state

(deftest test-app-state
  (let [app (app/build example-app)]
    (app/begin app)
    (is (vector?
         (test/run-sync! app [{msg/topic :example-transform, msg/type msg/init, :value "x"}])))
    (is (= (-> app :state deref :models :example-transform) "x"))))

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

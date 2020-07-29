(ns malli.util-test
  (:require [clojure.test :refer [deftest testing is are]]
            [malli.util :as mu]
            [malli.core :as m]))

(defn form= [& ?schemas]
  (apply = (map #(if (m/schema? %) (m/form %) %) ?schemas)))

(deftest equals-test
  (is (true? (mu/equals int? int?)))
  (is (true? (mu/equals [:map [:x int?]] [:map [:x int?]])))
  (is (false? (mu/equals [:map [:x {} int?]] [:map [:x int?]]))))

(deftest simplify-map-entry-test
  (are [entry expected]
    (is (= expected (mu/simplify-map-entry entry)))

    [:x 'int?] [:x 'int?]
    [:x nil 'int?] [:x 'int?]
    [:x {} 'int?] [:x 'int?]
    [:x {:optional false} 'int?] [:x 'int?]
    [:x {:optional false, :x 1} 'int?] [:x {:x 1} 'int?]
    [:x {:optional true} 'int?] [:x {:optional true} 'int?]))

(deftest merge-test
  (are [?s1 ?s2 expected]
    (= true (mu/equals expected (mu/merge ?s1 ?s2)))

    int? int? int?
    int? pos-int? pos-int?
    int? nil int?
    nil pos-int? pos-int?

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} pos-int?]]

    [:map [:x {:optional true} int?]]
    [:map [:x pos-int?]]
    [:map [:x pos-int?]]

    [:map [:x {:optional false} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} pos-int?]]

    [:map {:title "parameters"}
     [:parameters
      [:map
       [:query-params {:title "query1", :description "first"}
        [:map [:x int?]]]]]]
    [:map {:description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]
    [:map {:title "parameters", :description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :description "first", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]))

(deftest union-test
  (are [?s1 ?s2 expected]
    (= true (mu/equals expected (mu/union ?s1 ?s2)))

    int? int? int?
    int? pos-int? [:or int? pos-int?]
    int? nil int?
    nil pos-int? pos-int?

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x {:optional true} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map [:x {:optional false} int?]]
    [:map [:x {:optional true} pos-int?]]
    [:map [:x {:optional true} [:or int? pos-int?]]]

    [:map {:title "parameters"}
     [:parameters
      [:map
       [:query-params {:title "query1", :description "first"}
        [:map [:x int?]]]]]]
    [:map {:description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :summary "second"}
        [:map [:x string?] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]
    [:map {:title "parameters", :description "description"}
     [:parameters
      [:map
       [:query-params {:title "query2", :description "first", :summary "second"}
        [:map [:x [:or int? string?]] [:y int?]]]
       [:body-params
        [:map [:z int?]]]]]]))

(deftest update-properties-test
  (let [schema [:and {:x 0} int?]]
    (is (mu/equals [:and {:x 1} int?]
                   (mu/update-properties schema update :x inc)))
    (is (mu/equals [:and {:x 0, :joulu "loma"} int?]
                   (mu/update-properties schema assoc :joulu "loma")))))

(deftest open-closed-schema-test
  (let [open [:map {:title "map"}
              [:a int?]
              [:b {:optional true} int?]
              [:c [:map
                   [:d int?]]]]
        closed [:map {:title "map", :closed true}
                [:a int?]
                [:b {:optional true} int?]
                [:c [:map {:closed true}
                     [:d int?]]]]]

    (is (mu/equals closed (mu/closed-schema open)))
    (is (mu/equals open (mu/open-schema closed))))

  (testing "explicitely open maps not effected"
    (let [schema [:map {:title "map", :closed false}
                  [:a int?]
                  [:b {:optional true} int?]
                  [:c [:map {, :closed false}
                       [:d int?]]]]]

      (is (mu/equals schema (mu/closed-schema schema)))
      (is (mu/equals schema (mu/open-schema schema))))))

(deftest select-key-test
  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/select-keys schema []) [:map {:title "map"}]))
    (is (mu/equals (mu/select-keys schema nil) [:map {:title "map"}]))
    (is (mu/equals (mu/select-keys schema [:a]) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema #{:a}) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema '(:a)) [:map {:title "map"} [:a int?]]))
    (is (mu/equals (mu/select-keys schema [:a :b :c]) schema))
    (is (mu/equals (mu/select-keys schema [:a :b :extra])
                   [:map {:title "map"}
                    [:a int?]
                    [:b {:optional true} int?]]))))

(deftest get-test
  (is (form= (mu/get int? 0) nil))
  (is (mu/equals (mu/get [:map [:x int?]] :x) int?))
  (is (mu/equals (mu/get [:map [:x {:optional true} int?]] :x) int?))
  (is (mu/equals (mu/get [:vector int?] 0) int?))
  (is (mu/equals (mu/get [:list int?] 0) int?))
  (is (mu/equals (mu/get [:set int?] 0) int?))
  (is (mu/equals (mu/get [:sequential int?] 0) int?))
  (is (mu/equals (mu/get [:or int? pos-int?] 1) pos-int?))
  (is (mu/equals (mu/get [:and int? pos-int?] 1) pos-int?))
  (is (mu/equals (mu/get [:tuple int? pos-int?] 1) pos-int?))
  (is (form= (mu/get [:map [:x int?]] :y)
             (mu/get [:vector int?] 1)
             nil))
  (is (mu/equals (mu/get [:map-of int? pos-int?] 0) int?))
  (is (mu/equals (mu/get [:map-of int? pos-int?] 1) pos-int?))
  (is (form= (mu/get [:ref {:registry {::a int?, ::b string?}} ::a] 0) ::a))
  (is (mu/equals (mu/get [:schema int?] 0) int?)))

(deftest get-in-test
  (is (mu/equals boolean?
                 (mu/get-in
                   [:map
                    [:x [:vector
                         [:list
                          [:set
                           [:sequential
                            [:tuple int? [:map [:y [:maybe boolean?]]]]]]]]]]
                   [:x 0 0 0 0 1 :y 0])))
  (is (mu/equals [:maybe [:tuple int? boolean?]]
                 (mu/get-in [:maybe [:tuple int? boolean?]] [])))
  (is (form= (mu/get-in [:ref {:registry {::a int?, ::b string?}} ::a] [0]) ::a))
  (is (mu/equals (mu/get-in [:ref {:registry {::a int?, ::b string?}} ::a] [0 0]) int?))
  (is (form= (mu/get-in [:schema {:registry {::a int?, ::b string?}} ::a] [0]) ::a))
  (is (mu/equals (mu/get-in [:schema {:registry {::a int?, ::b string?}} ::a] [0 0]) int?)))

(deftest dissoc-test
  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/dissoc schema :a)
                   [:map {:title "map"}
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/dissoc schema :b)
                   [:map {:title "map"}
                    [:a int?]
                    [:c string?]]))))

(deftest assoc-test
  (is (mu/equals (mu/assoc [:vector int?] 0 string?) [:vector string?]))
  (is (mu/equals (mu/assoc [:tuple int? int?] 1 string?) [:tuple int? string?]))
  (is (mu/equals (mu/assoc [:tuple int? int?] 2 string?) [:tuple int? int? string?]))
  (is (mu/equals (mu/assoc [:and int? int?] 1 string?) [:and int? string?]))
  (is (mu/equals (mu/assoc [:maybe int?] 0 string?) [:maybe string?]))
  (is (mu/equals (mu/assoc [:map [:x int?] [:y int?]] :x nil) [:map [:y int?]]))
  (is (mu/equals (mu/assoc [:map-of int? int?] 0 string?) [:map-of string? int?]))
  (is (mu/equals (mu/assoc [:map-of int? int?] 1 string?) [:map-of int? string?]))
  (is (mu/equals (mu/assoc [:ref {:registry {::a int?, ::b string?}} ::a] 0 ::b) [:ref {:registry {::a int?, ::b string?}} ::b]))
  (is (mu/equals (mu/assoc [:schema int?] 0 string?) [:schema string?]))

  (testing "invalid assoc throws"
    (are [schema i]
      (is (thrown? #?(:clj Exception, :cljs js/Error) (mu/assoc schema i string?)))

      int? 1
      [:vector int?] 1
      [:tuple int? int?] 3
      [:or int? int?] 3
      [:and int? int?] 3
      [:maybe int?] 2
      [:map-of int? int?] 2
      [:ref {:registry {::a int?}} ::a] 2
      [:schema int?] 2))

  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/assoc schema :a string?)
                   [:map {:title "map"}
                    [:a string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/assoc schema [:a {:optional true}] string?)
                   [:map {:title "map"}
                    [:a {:optional true} string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/assoc schema :b string?)
                   [:map {:title "map"}
                    [:a int?]
                    [:b string?]
                    [:c string?]]))))

(deftest update-test
  (is (mu/equals (mu/update [:vector int?] 0 (constantly string?)) [:vector string?]))
  (is (mu/equals (mu/update [:tuple int? int?] 1 (constantly string?)) [:tuple int? string?]))
  (is (mu/equals (mu/update [:tuple int? int?] 2 (constantly string?)) [:tuple int? int? string?]))
  (is (mu/equals (mu/update [:or int? int?] 1 (constantly string?)) [:or int? string?]))
  (is (mu/equals (mu/update [:or int? int?] 2 (constantly string?)) [:or int? int? string?]))
  (is (mu/equals (mu/update [:and int? int?] 1 (constantly string?)) [:and int? string?]))
  (is (mu/equals (mu/update [:and int? int?] 2 (constantly string?)) [:and int? int? string?]))
  (is (mu/equals (mu/update [:maybe int?] 0 (constantly string?)) [:maybe string?]))
  (is (mu/equals (mu/update [:map [:x int?] [:y int?]] :x (constantly nil)) [:map [:y int?]]))
  (is (mu/equals (mu/update [:ref {:registry {::a int?, ::b string?}} ::a] 0 (constantly ::b)) [:ref {:registry {::a int?, ::b string?}} ::b]))
  (is (mu/equals (mu/update [:schema int?] 0 (constantly string?)) [:schema string?]))

  (let [schema [:map {:title "map"}
                [:a int?]
                [:b {:optional true} int?]
                [:c string?]]]
    (is (mu/equals (mu/update schema :a mu/update-properties assoc :title "a")
                   [:map {:title "map"}
                    [:a [int? {:title "a"}]]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/update schema :a (constantly string?))
                   [:map {:title "map"}
                    [:a string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/update schema [:a {:optional true}] (constantly string?))
                   [:map {:title "map"}
                    [:a {:optional true} string?]
                    [:b {:optional true} int?]
                    [:c string?]]))
    (is (mu/equals (mu/update schema :b (constantly string?))
                   [:map {:title "map"}
                    [:a int?]
                    [:b string?]
                    [:c string?]]))))

(deftest assoc-in-test
  (is (mu/equals (mu/assoc-in [:vector int?] [0] string?) [:vector string?]))
  (is (mu/equals (mu/assoc-in [:tuple int? int?] [1] string?) [:tuple int? string?]))
  (is (mu/equals (mu/assoc-in [:tuple int? int?] [2] string?) [:tuple int? int? string?]))
  (is (mu/equals (mu/assoc-in [:or int? int?] [1] string?) [:or int? string?]))
  (is (mu/equals (mu/assoc-in [:or int? int?] [2] string?) [:or int? int? string?]))
  (is (mu/equals (mu/assoc-in [:and int? int?] [1] string?) [:and int? string?]))
  (is (mu/equals (mu/assoc-in [:and int? int?] [2] string?) [:and int? int? string?]))
  (is (mu/equals (mu/assoc-in [:maybe int?] [0] string?) [:maybe string?]))
  (is (mu/equals (mu/assoc-in nil [:a [:b {:optional true}] :c :d] int?)
                 [:map [:a [:map [:b {:optional true} [:map [:c [:map [:d int?]]]]]]]]))
  (is (mu/equals (mu/assoc-in [:map] [:a :b :c :d] int?)
                 [:map [:a [:map [:b [:map [:c [:map [:d int?]]]]]]]]))
  (is (mu/equals (mu/assoc-in [:ref {:registry {::a int?, ::b string?}} ::a] [0] ::b) [:ref {:registry {::a int?, ::b string?}} ::b]))
  (is (mu/equals (mu/assoc-in [:schema int?] [0] string?) [:schema string?])))

(deftest update-in-test
  (is (mu/equals (mu/update-in [:vector int?] [0] (constantly string?)) [:vector string?]))
  (is (mu/equals (mu/update-in [:tuple int? int?] [1] (constantly string?)) [:tuple int? string?]))
  (is (mu/equals (mu/update-in [:tuple int? int?] [2] (constantly string?)) [:tuple int? int? string?]))
  (is (mu/equals (mu/update-in [:or int? int?] [1] (constantly string?)) [:or int? string?]))
  (is (mu/equals (mu/update-in [:or int? int?] [2] (constantly string?)) [:or int? int? string?]))
  (is (mu/equals (mu/update-in [:and int? int?] [1] (constantly string?)) [:and int? string?]))
  (is (mu/equals (mu/update-in [:and int? int?] [2] (constantly string?)) [:and int? int? string?]))
  (is (mu/equals (mu/update-in [:maybe int?] [0] (constantly string?)) [:maybe string?]))
  (is (mu/equals (mu/update-in nil [:a [:b {:optional true}] :c :d] (constantly int?))
                 [:map [:a [:map [:b {:optional true} [:map [:c [:map [:d int?]]]]]]]]))
  (is (mu/equals (mu/update-in [:map] [:a :b :c :d] (constantly int?))
                 [:map [:a [:map [:b [:map [:c [:map [:d int?]]]]]]]]))
  (is (mu/equals (mu/update-in [:ref {:registry {::a int?, ::b string?}} ::a] [0] (constantly ::b)) [:ref {:registry {::a int?, ::b string?}} ::b]))
  (is (mu/equals (mu/update-in [:schema int?] [0] (constantly string?)) [:schema string?])))

(deftest optional-keys-test
  (let [schema [:map [:x int?] [:y int?]]]
    (is (mu/equals (mu/optional-keys schema)
                   [:map [:x {:optional true} int?] [:y {:optional true} int?]]))
    (is (mu/equals (mu/optional-keys schema [:x :extra nil])
                   [:map [:x {:optional true} int?] [:y int?]]))))

(deftest required-keys-test
  (let [schema [:map [:x {:optional true} int?] [:y {:optional false} int?]]]
    (is (mu/equals (mu/required-keys schema)
                   [:map [:x int?] [:y int?]]))
    (is (mu/equals (mu/required-keys schema [:x :extra nil])
                   [:map [:x int?] [:y {:optional false} int?]]))))

(deftest find-first-test
  (let [schema [:map
                [:x int?]
                [:y [:vector [:tuple
                              [:maybe int?]
                              [:or [:and {:salaisuus "turvassa"} boolean?] int?]
                              [:schema {:salaisuus "vaarassa"} false?]]]]
                [:z [:string {:salaisuus "piilossa"}]]]]

    (let [walked-properties (atom [])]
      (is (= "turvassa" (mu/find-first
                          schema
                          (fn [s _in _options]
                            (some->> s m/properties (swap! walked-properties conj))
                            (some-> s m/properties :salaisuus)))))
      (is (= [{:salaisuus "turvassa"}] @walked-properties)))

    (let [walked-properties (atom [])]
      (is (= "vaarassa" (mu/find-first
                          schema
                          (fn [s _in _options]
                            (some->> s m/properties (swap! walked-properties conj))
                            (some-> s m/properties :salaisuus #{"vaarassa"})))))
      (is (= [{:salaisuus "turvassa"}
              {:salaisuus "vaarassa"}] @walked-properties)))))

(deftest path-schema-test
  (let [schema [:and
                [:map
                 [:a int?]
                 [:b [:set boolean?]]
                 [:c [:vector
                      [:and
                       [:fn '(constantly true)]
                       [:map
                        [:d string?]]]]]]
                [:fn '(constantly true)]]]

    (testing "retains original order"
      (= [[]
          [:a]
          [:b]
          [:b :malli.core/in]
          [:c]
          [:c :malli.core/in]
          [:c :malli.core/in :d]]
         (keys (mu/path-schemas schema))))

    (testing "path-schemas are correct"
      (is (= (->> {[] [:and
                       [:map
                        [:a int?]
                        [:b [:set boolean?]]
                        [:c [:vector
                             [:and
                              [:fn '(constantly true)]
                              [:map
                               [:d string?]]]]]]
                       [:fn '(constantly true)]]
                   [:a] int?
                   [:b] [:set boolean?]
                   [:b :malli.core/in] boolean?
                   [:c] [:vector
                         [:and
                          [:fn '(constantly true)]
                          [:map
                           [:d string?]]]]
                   [:c :malli.core/in] [:and
                                        [:fn '(constantly true)]
                                        [:map
                                         [:d string?]]]
                   [:c :malli.core/in :d] string?}
                  (map (fn [[k v]] [k (m/form v)])))
             (->> (mu/path-schemas schema)
                  (map (fn [[k v]] [k (m/form v)]))))))))

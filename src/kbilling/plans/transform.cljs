(ns kbilling.plans.transform)

(def BigNumber (js/require "bignumber.js"))


(def k_ (memoize (fn [a & others] (keyword (apply str (name a) (for [s others, _ [\_ (name s)]] _))))))


(defn aggregate [plan cycles vars buys]
  (into {} (for [[ck c] (:$cycles plan) :when (or (= ck :$subscription) (contains? cycles ck))
                 [acck acc] c :when (contains? buys acck)
                 [aggk agg] acc
                 :let [k (k_ ck acck aggk)]]
             [k ((:$aggr agg) (or (k vars) ((:$init agg))) (acck buys))])))


(defn calculate-costs [plan cycles cur]
  (into {} (for [[ck c] (:$cycles plan) :when (or (= ck :$subscription) (contains? cycles ck))
                 [acck acc] c :let [cost-fn (:$cost acc)] :when cost-fn]
             [(k_ ck acck :$cost) (cost-fn cur)])))

(defn +bign [x y] (.plus (BigNumber. (or x 0)) (BigNumber. (or y 0))))
(defn -bign [x y] (.minus (BigNumber. (or x 0)) (BigNumber. (or y 0))))

(defn apply-costs [vars costs]
  (let [cost-deltas (for [[costk costv] costs
                          :let [costv-prev (or (costk vars) 0)
                                cost-delta (-bign costv costv-prev)
                                acck (keyword ((clojure.string/split costk #"_") 1))]]
                      [acck cost-delta])]
    (into {} (->> cost-deltas
                  (group-by #(% 0))
                  (map (fn [[acck costs]] [acck (->> costs (map #(% 1)) (reduce +bign 0))]))
                  (map (fn [[acck delta]] [acck (-bign (acck vars) delta)]))))))

(defn calculate-values [plan cur]
  (into {} (for [[val-k val-fn] (or (:$values plan) [])]
             [val-k (val-fn cur)])))

(defn calculate-no-values [plan cycles vars cur]
  (let [costs (calculate-costs plan cycles cur)]
    (merge costs (apply-costs vars costs))))

(defn calculate [plan cycles vars cur]
  (let [cur (merge vars (calculate-no-values plan cycles vars cur))]
    (merge cur (calculate-values plan cur))))


(defn transitive-image [f z x]
  (if (contains? z x) z (reduce #(transitive-image f %1 %2) (conj z x) (f x))))

(defn transitive-billing-cycles [plan ck]
  (transitive-image #(or (-> plan :$cycles % :$begin) #{}) #{} ck))

(def acc-keys
  (memoize #(into #{} (for [[_ c] (:$cycles %)
                            [acck _] c :when (not (contains? #{:$begin :$duration} acck))]
                        acck))))

(defn init-vars [plan cycles vars]
  (let [acc-vars (into {} (for [acck (acc-keys plan)]
                            [acck (or (acck vars) 0)]))
        cost-vars (into {} (for [[ck c] (:$cycles plan) :when (contains? cycles ck)
                                 [acck acc] c :when (and (not (contains? #{:$begin :$duration} acck)) (:$cost acc))]
                             [(k_ ck acck :$cost) 0]))
        agg-vars (into {} (for [[ck c] (:$cycles plan) :when (contains? cycles ck)
                                [acck acc] c :when (not (contains? #{:$begin :$duration} acck))
                                [aggk agg] acc :let [agg-init (:$init agg)] :when agg-init]
                            [(k_ ck acck aggk) (agg-init)]))]
    (merge acc-vars cost-vars agg-vars)))


(defn apply-add-buy [plan cycles vars adds buys]
  (let [vars (merge-with +bign vars adds buys)
        cur (merge vars (aggregate plan cycles vars buys))]
    (merge cur (calculate plan cycles vars cur))))


(defn subscribe [plan cycles vars])

(defn cycle-begin [plan cycles vars cycle])

(defn transform [inobj])

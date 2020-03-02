(ns dfym.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [dfym.core-test]
   [dfym.common-test]))

(enable-console-print!)

(doo-tests 'dfym.core-test
           'dfym.common-test)

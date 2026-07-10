# `motor-retium`

**🚧 WORK IN PROGRESS**

`motor-retium` is a parallel, demand-driven interaction-net engine built for computationally hard problems. The reduction strategy is _Weak Reduction to Interface Normal Form (WRINF)_ [^wrinf], so that onely needed interactions are executed. Automatic lock-free parallelisme is realized by reducing strict operands in parallel, treating duplicators as a single shared resource. _Heartbeat scheduling_ [^heartbeat-scheduling] is used to control the rate of task promotion. The operational semantics is based on a polarized variant of Mackie's _closed reduction_ [^closed-reduction]: closures become strict in captures and thus provide more opportunity for parallelization.

TODO: finish the README.

## References

[^wrinf]: Pinto, Jorge Sousa. "Weak reduction and garbage collection in interaction nets." Electronic Notes in Theoretical Computer Science 86.4 (2003): 625-640.

[^heartbeat-scheduling]: Acar, Umut A., et al. "Heartbeat scheduling: Provable efficiency for nested parallelism." Proceedings of the 39th ACM SIGPLAN Conference on Programming Language Design and Implementation. 2018.

[^closed-reduction]: Mackie, Ian. "An interaction net implementation of closed reduction." Symposium on Implementation and Application of Functional Languages. Berlin, Heidelberg: Springer Berlin Heidelberg, 2008.

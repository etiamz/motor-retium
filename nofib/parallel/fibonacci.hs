-- No sharing, maximal parallelisme; mirrors `fibonacci.rete`.

-- \$ fourmolu --mode inplace nofib/parallel/fibonacci.hs

import Data.Word (Word64)
import System.Environment (getArgs)

defaultInput :: Word64
defaultInput = 37

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (nfib n)

nfib :: Word64 -> Word64
nfib x =
    if x < 2 then 1 else nfib (x - 2) + nfib (x - 1) + 1

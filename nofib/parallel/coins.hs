-- No sharing, high parallelisme; mirrors `coins.rete`.

-- \$ fourmolu --mode inplace nofib/parallel/coins.hs

import Data.Word (Word64)
import System.Environment (getArgs)

data List a = Nil | Cons a (List a)

data Pair a b = Pair a b

defaultInput :: Word64
defaultInput = 950

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print
        ( show
            ( payN
                n
                ( Cons
                    (Pair 250 55)
                    ( Cons
                        (Pair 100 88)
                        ( Cons
                            (Pair 25 88)
                            ( Cons
                                (Pair 10 99)
                                ( Cons
                                    (Pair 5 122)
                                    (Cons (Pair 1 177) Nil)
                                )
                            )
                        )
                    )
                )
            )
        )

payN :: Word64 -> List (Pair Word64 Word64) -> Word64
payN val coins =
    if val == 0
        then 1
        else case coins of
            Nil -> 0
            Cons cq coins -> case cq of
                Pair c _ | c > val -> payN val coins
                Pair c q ->
                    let coins' =
                            if q == 1 then coins else Cons (Pair c (q - 1)) coins
                     in let left = payN (val - c) coins'
                         in let right = payN val coins
                             in left + right

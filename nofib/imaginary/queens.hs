-- No sharing, low parallelisme; mirrors `queens.rete`.

-- \$ fourmolu --mode inplace nofib/imaginary/queens.hs

import Data.Int (Int64)
import Data.Word (Word64)
import System.Environment (getArgs)
import Prelude hiding (length)

data List a = Nil | Cons a (List a)

defaultInput :: Int64
defaultInput = 8

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (nsoln n)

nsoln :: Int64 -> Word64
nsoln nq =
    length (gen nq nq)

gen :: Int64 -> Int64 -> List (List Int64)
gen nq n =
    if n == 0 then Cons Nil Nil else expand nq (gen nq (n - 1))

expand :: Int64 -> List (List Int64) -> List (List Int64)
expand nq boards =
    case boards of
        Nil -> Nil
        Cons b xs -> extend nq b 1 (expand nq xs)

extend :: Int64 -> List Int64 -> Int64 -> List (List Int64) -> List (List Int64)
extend nq b q acc =
    if q > nq
        then acc
        else
            if safe q 1 b
                then Cons (Cons q b) (extend nq b (q + 1) acc)
                else extend nq b (q + 1) acc

safe :: Int64 -> Int64 -> List Int64 -> Bool
safe x d board =
    case board of
        Nil -> True
        Cons q l -> x /= q && x /= q + d && x /= q - d && safe x (d + 1) l

length :: List a -> Word64
length xs =
    case xs of Nil -> 0; Cons _ xs -> 1 + length xs

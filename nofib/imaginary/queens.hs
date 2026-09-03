-- Full sharing, substantial parallelisme; mirrors `queens.rete`.

-- \$ fourmolu --mode inplace nofib/imaginary/queens.hs

import Data.Int (Int64)
import Data.Word (Word64)
import System.Environment (getArgs)
import Prelude hiding (concatMap, enumFromTo, length)

data List a = Nil | Cons a (List a)

defaultInput :: Int64
defaultInput = 10

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (show (nsoln n))

nsoln :: Int64 -> Word64
nsoln nq =
    length (gen nq nq)

gen :: Int64 -> Int64 -> List (List Int64)
gen nq n =
    if n == 0
        then Cons Nil Nil
        else
            concatMap
                ( \b ->
                    concatMap
                        (\q -> if safe q 1 b then Cons (Cons q b) Nil else Nil)
                        (enumFromTo 1 nq)
                )
                (gen nq (n - 1))

safe :: Int64 -> Int64 -> List Int64 -> Bool
safe x d board =
    case board of
        Nil -> True
        Cons q l -> x /= q && x /= q + d && x /= q - d && safe x (d + 1) l

concatMap :: (a -> List b) -> List a -> List b
concatMap f xs =
    case xs of
        Nil -> Nil
        Cons x xs -> append (f x) (concatMap f xs)

append :: List a -> List a -> List a
append xs ys =
    case xs of
        Nil -> ys
        Cons x xs -> Cons x (append xs ys)

enumFromTo :: Int64 -> Int64 -> List Int64
enumFromTo n m =
    if n > m then Nil else Cons n (enumFromTo (n + 1) m)

length :: List a -> Word64
length xs =
    case xs of Nil -> 0; Cons _ xs -> 1 + length xs

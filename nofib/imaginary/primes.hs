-- Full sharing, low parallelisme; mirrors `primes.rete`.

-- \$ fourmolu --mode inplace nofib/imaginary/primes.hs

import Data.Word (Word64)
import System.Environment (getArgs)
import Prelude hiding (enumFromTo, filter, head, iterate, map)

data List a = Nil | Cons a (List a)

defaultInput :: Word64
defaultInput = 1000

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (show (prime n))

prime :: Word64 -> Word64
prime n =
    index (map head (iterate theFilter (enumFromTo 2 (n * n)))) n

theFilter :: List Word64 -> List Word64
theFilter xs =
    let Cons n ns = xs
     in filter (isdivs n) ns

isdivs :: Word64 -> Word64 -> Bool
isdivs n x =
    x `rem` n /= 0

filter :: (a -> Bool) -> List a -> List a
filter p xs =
    case xs of
        Nil -> Nil
        Cons x xs | p x -> Cons x (filter p xs)
        Cons _ xs -> filter p xs

iterate :: (a -> a) -> a -> List a
iterate f x =
    Cons x (iterate f (f x))

map :: (a -> b) -> List a -> List b
map f xs =
    case xs of Nil -> Nil; Cons x xs -> Cons (f x) (map f xs)

head :: List a -> a
head xs =
    let Cons x _ = xs
     in x

index :: List a -> Word64 -> a
index xs n =
    case xs of
        Cons x _ | n == 0 -> x
        Cons _ xs -> index xs (n - 1)

enumFromTo :: Word64 -> Word64 -> List Word64
enumFromTo n m =
    if n > m then Nil else Cons n (enumFromTo (n + 1) m)

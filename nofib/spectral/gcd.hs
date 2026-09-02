-- Full sharing, low parallelisme; mirrors `gcd.rete`.

-- \$ fourmolu --mode inplace nofib/spectral/gcd.hs

import System.Environment (getArgs)
import Prelude hiding (abs, concatMap, enumFromTo, map)

data List a = Nil | Cons a (List a)

data Pair a b = Pair a b

data Triple a b c = Triple a b c

defaultInput :: Integer
defaultInput = 100

main :: IO ()
main = do
    args <- getArgs
    let d = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (maxGcdE d)

maxGcdE :: Integer -> Integer
maxGcdE d =
    let n = 5000
     in let m = 10000
         in let ns = enumFromTo n (n + d)
             in let ms = enumFromTo m (m + d)
                 in let pairs = concatMap (\x -> concatMap (\y -> Cons (Pair x y) Nil) ms) ns
                     in let tripls = map (\xy -> let Pair x y = xy in Triple x y (gcdE x y)) pairs
                         in let rs = map (\xyg -> let Triple _ _ guv = xyg in let Triple g u v = guv in abs (g + u + v)) tripls
                             in max' rs

gcdE :: Integer -> Integer -> Triple Integer Integer Integer
gcdE x y =
    if x == 0
        then Triple y 0 1
        else g (Triple 1 0 x) (Triple 0 1 y)

g :: Triple Integer Integer Integer -> Triple Integer Integer Integer -> Triple Integer Integer Integer
g u v =
    let Triple u1 u2 u3 = u
     in let Triple v1 v2 v3 = v
         in if v3 == 0
                then Triple u3 u1 u2
                else
                    let q = u3 `quot` v3
                     in let r = u3 `rem` v3
                         in g (Triple v1 v2 v3) (Triple (u1 - q * v1) (u2 - q * v2) r)

max' :: List Integer -> Integer
max' xs =
    let Cons x rest = xs
     in case rest of
            Nil -> x
            Cons y ys -> if x < y then max' (Cons y ys) else max' (Cons x ys)

abs :: Integer -> Integer
abs x =
    if x < 0 then negate x else x

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

map :: (a -> b) -> List a -> List b
map f xs =
    case xs of Nil -> Nil; Cons x xs -> Cons (f x) (map f xs)

enumFromTo :: Integer -> Integer -> List Integer
enumFromTo n m =
    if n > m then Nil else Cons n (enumFromTo (n + 1) m)

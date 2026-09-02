-- Full sharing, low parallelisme; mirrors `lcss.rete`.

-- \$ fourmolu --mode inplace nofib/spectral/lcss.hs

import Data.Int (Int64)
import System.Environment (getArgs)
import Prelude hiding (drop, elem, enumFromThenTo, id, length, map, reverse, snd, take, zip)

data List a = Nil | Cons a (List a)

data Pair a b = Pair a b

defaultInput :: (Int64, Int64, Int64, Int64, Int64, Int64)
defaultInput = (1, 2, 1000, 500, 501, 1500)

main :: IO ()
main = do
    args <- getArgs
    let (a, b, c, d, e, f) = case args of
            (a' : b' : c' : d' : e' : f' : _) -> (read a', read b', read c', read d', read e', read f')
            _ -> defaultInput
    print (renderList (lcss (enumFromThenTo a b c) (enumFromThenTo d e f)))

lcss :: List Int64 -> List Int64 -> List Int64
lcss xs ys =
    algc (length xs) (length ys) xs ys Nil

algc :: Int64 -> Int64 -> List Int64 -> List Int64 -> List Int64 -> List Int64
algc m n xs ys =
    case ys of
        Nil -> id
        Cons _ _ ->
            let Cons x rest = xs
             in case rest of
                    Nil -> if elem x ys then Cons x else id
                    Cons _ _ ->
                        let m2 = m `div` 2
                         in let xs1 = take m2 xs
                             in let xs2 = drop m2 xs
                                 in let l1 = algb xs1 ys
                                     in let l2 = reverse (algb (reverse xs2) (reverse ys))
                                         in let k = findk 0 0 (-1) (zip l1 l2)
                                             in algc m2 k xs1 (take k ys) . algc (m - m2) (n - k) xs2 (drop k ys)

findk :: Int64 -> Int64 -> Int64 -> List (Pair Int64 Int64) -> Int64
findk k km m xys =
    case xys of
        Nil -> km
        Cons xy xys -> case xy of
            Pair x y | x + y >= m -> findk (k + 1) k (x + y) xys
            Pair _ _ -> findk (k + 1) km m xys

algb :: List Int64 -> List Int64 -> List Int64
algb xs ys =
    Cons 0 (algb1 xs (map (\y -> Pair y 0) ys))

algb1 :: List Int64 -> List (Pair Int64 Int64) -> List Int64
algb1 xs ys' =
    case xs of
        Nil -> map snd ys'
        Cons x xs -> algb1 xs (algb2 x 0 0 ys')

algb2 :: Int64 -> Int64 -> Int64 -> List (Pair Int64 Int64) -> List (Pair Int64 Int64)
algb2 x k0j1 k1j1 yks =
    case yks of
        Nil -> Nil
        Cons yk ys ->
            let Pair y k0j = yk
             in let kjcurr = if x == y then k0j1 + 1 else max k1j1 k0j
                 in Cons (Pair y kjcurr) (algb2 x k0j kjcurr ys)

renderList :: List Int64 -> String
renderList xs =
    case xs of
        Nil -> ""
        Cons x xs -> show x ++ " " ++ renderList xs

map :: (a -> b) -> List a -> List b
map f xs =
    case xs of Nil -> Nil; Cons x xs -> Cons (f x) (map f xs)

elem :: Int64 -> List Int64 -> Bool
elem x ys =
    case ys of Nil -> False; Cons y ys -> x == y || elem x ys

reverse :: List a -> List a
reverse xs =
    reverseAux Nil xs

reverseAux :: List a -> List a -> List a
reverseAux acc xs =
    case xs of Nil -> acc; Cons x xs -> reverseAux (Cons x acc) xs

zip :: List a -> List b -> List (Pair a b)
zip xs ys =
    case xs of
        Nil -> Nil
        Cons x xs -> case ys of
            Nil -> Nil
            Cons y ys -> Cons (Pair x y) (zip xs ys)

take :: Int64 -> List a -> List a
take n xs =
    if n <= 0
        then Nil
        else case xs of
            Nil -> Nil
            Cons x xs -> Cons x (take (n - 1) xs)

drop :: Int64 -> List a -> List a
drop n xs =
    if n <= 0
        then xs
        else case xs of
            Nil -> Nil
            Cons _ xs -> drop (n - 1) xs

length :: List a -> Int64
length xs =
    case xs of Nil -> 0; Cons _ xs -> 1 + length xs

enumFromThenTo :: Int64 -> Int64 -> Int64 -> List Int64
enumFromThenTo n n' m =
    let step = n' - n
     in if (step < 0 && n < m) || (step >= 0 && n > m)
            then Nil
            else Cons n (enumFromThenTo n' (n' + step) m)

snd :: Pair a b -> b
snd p =
    let Pair _ y = p
     in y

id :: a -> a
id x =
    x

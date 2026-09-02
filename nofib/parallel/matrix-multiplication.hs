-- Full sharing, substantial parallelisme; mirrors `matrix-multiplication.rete`.

-- \$ fourmolu --mode inplace nofib/parallel/matrix-multiplication.hs

import Data.Int (Int64)
import System.Environment (getArgs)
import Prelude hiding (enumFromTo, head, map, replicate, tail)

data List a = Nil | Cons a (List a)

defaultInput :: Int64
defaultInput = 150

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (renderMatrix (multMatricesTr (m1 n) (transpose (m1 n))))

multMatricesTr :: List (List Int64) -> List (List Int64) -> List (List Int64)
multMatricesTr m1 m2 =
    map (\row -> map (\col -> prodEscalar row col) m2) m1

prodEscalar :: List Int64 -> List Int64 -> Int64
prodEscalar v1 v2 =
    addProd v1 v2 0

addProd :: List Int64 -> List Int64 -> Int64 -> Int64
addProd v1 v2 acc =
    case v1 of
        Nil -> acc
        Cons v vs -> case v2 of
            Nil -> acc
            Cons w ws -> addProd vs ws (acc + v * w)

transpose :: List (List a) -> List (List a)
transpose m =
    case m of
        Nil -> Nil
        Cons row _ -> case row of
            Nil -> Nil
            Cons _ _ -> Cons (map head m) (transpose (map tail m))

m1 :: Int64 -> List (List Int64)
m1 size = replicate size (enumFromTo 1 size)

renderMatrix :: List (List Int64) -> String
renderMatrix m =
    case m of
        Nil -> ""
        Cons row rows -> renderRow row ++ "\n" ++ renderMatrix rows

renderRow :: List Int64 -> String
renderRow row =
    case row of
        Nil -> ""
        Cons x xs -> show x ++ " " ++ renderRow xs

map :: (a -> b) -> List a -> List b
map f xs =
    case xs of Nil -> Nil; Cons x xs -> Cons (f x) (map f xs)

head :: List a -> a
head xs =
    let Cons x _ = xs
     in x

tail :: List a -> List a
tail xs =
    let Cons _ rest = xs
     in rest

replicate :: Int64 -> a -> List a
replicate n x =
    if n == 0 then Nil else Cons x (replicate (n - 1) x)

enumFromTo :: Int64 -> Int64 -> List Int64
enumFromTo n m =
    if n > m then Nil else Cons n (enumFromTo (n + 1) m)

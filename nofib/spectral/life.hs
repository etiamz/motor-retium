-- Full sharing, low parallelisme; mirrors `life.rete`.

-- \$ fourmolu --mode inplace nofib/spectral/life.hs

import Data.Word (Word64)
import System.Environment (getArgs)
import Prelude hiding (concat, enumFrom, foldr, init, iterate, last, map, null, tail, take, zip, zip3, zipWith3)

data List a = Nil | Cons a (List a)

data Pair a b = Pair a b

data Triple a b c = Triple a b c

defaultInput :: Word64
defaultInput = 27

main :: IO ()
main = do
    args <- getArgs
    let sz = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (show (life sz))

life :: Word64 -> Word64
life sz =
    let generations =
            (map disp . zip (map show (enumFrom 0)) . limit . iterate (gen sz))
                ( take
                    sz
                    ( append
                        (map (take sz . (\xs -> append xs (copy sz 0))) start)
                        (copy sz (copy sz 0))
                    )
                )
     in fromIntegral (length (last generations))

{- FOURMOLU_DISABLE -}
start :: List (List Word64)
start =
    Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons Nil (Cons (Cons 0 (Cons 0 (Cons 0 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 0 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 0 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 0 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 1 (Cons 0 Nil))))))))))))))))))))))))))) Nil))))))))))))))
{- FOURMOLU_ENABLE -}

gen :: Word64 -> List (List Word64) -> List (List Word64)
gen n board =
    map row (shift (copy n 0) board)

row :: Triple (List Word64) (List Word64) (List Word64) -> List Word64
row rows =
    let Triple last this next = rows
     in zipWith3 elt (shift 0 last) (shift 0 this) (shift 0 next)

elt :: Triple Word64 Word64 Word64 -> Triple Word64 Word64 Word64 -> Triple Word64 Word64 Word64 -> Word64
elt left middle right =
    let Triple a b c = left
     in let Triple d e f = middle
         in let Triple g h i = right
             in let tot = a + b + c + d + f + g + h + i
                 in if tot < 2 || tot > 3
                        then 0
                        else if tot == 3 then 1 else e

shiftr :: a -> List a -> List a
shiftr x xs =
    Cons x (init xs)

shiftl :: a -> List a -> List a
shiftl x xs =
    append (tail xs) (Cons x Nil)

shift :: a -> List a -> List (Triple a a a)
shift x xs =
    zip3 (shiftr x xs) xs (shiftl x xs)

copy :: Word64 -> a -> List a
copy n x =
    if n == 0 then Nil else Cons x (copy (n - 1) x)

disp :: Pair String (List (List Word64)) -> String
disp item =
    let Pair gen xss = item
     in gen ++ "\n\n" ++ (foldr (glue "\n") "" . map (concat . map star)) xss

star :: Word64 -> String
star cell =
    if cell == 0 then "  " else " o"

glue :: String -> String -> String -> String
glue s xs ys =
    xs ++ s ++ ys

limit :: List (List (List Word64)) -> List (List (List Word64))
limit boards =
    let Cons x rest = boards
     in case rest of
            Cons y _ | equalBoard x y -> Cons x Nil
            Cons y xs -> Cons x (limit (Cons y xs))

equalBoard :: List (List Word64) -> List (List Word64) -> Bool
equalBoard xs ys =
    case xs of
        Nil -> null ys
        Cons x xs -> case ys of
            Nil -> False
            Cons y ys -> equalRow x y && equalBoard xs ys

equalRow :: List Word64 -> List Word64 -> Bool
equalRow xs ys =
    case xs of
        Nil -> null ys
        Cons x xs -> case ys of
            Nil -> False
            Cons y ys -> x == y && equalRow xs ys

enumFrom :: Word64 -> List Word64
enumFrom n =
    Cons n (enumFrom (n + 1))

iterate :: (a -> a) -> a -> List a
iterate f x =
    Cons x (iterate f (f x))

map :: (a -> b) -> List a -> List b
map f xs =
    case xs of Nil -> Nil; Cons x xs -> Cons (f x) (map f xs)

foldr :: (a -> b -> b) -> b -> List a -> b
foldr f z xs =
    case xs of Nil -> z; Cons x xs -> f x (foldr f z xs)

concat :: List String -> String
concat xs =
    case xs of Nil -> ""; Cons x xs -> x ++ concat xs

append :: List a -> List a -> List a
append xs ys =
    case xs of Nil -> ys; Cons x xs -> Cons x (append xs ys)

zip :: List a -> List b -> List (Pair a b)
zip xs ys =
    case xs of
        Nil -> Nil
        Cons x xs -> case ys of
            Nil -> Nil
            Cons y ys -> Cons (Pair x y) (zip xs ys)

zip3 :: List a -> List b -> List c -> List (Triple a b c)
zip3 xs ys zs =
    case xs of
        Nil -> Nil
        Cons x xs -> case ys of
            Nil -> Nil
            Cons y ys -> case zs of
                Nil -> Nil
                Cons z zs -> Cons (Triple x y z) (zip3 xs ys zs)

zipWith3 :: (a -> b -> c -> d) -> List a -> List b -> List c -> List d
zipWith3 f xs ys zs =
    case xs of
        Nil -> Nil
        Cons x xs -> case ys of
            Nil -> Nil
            Cons y ys -> case zs of
                Nil -> Nil
                Cons z zs -> Cons (f x y z) (zipWith3 f xs ys zs)

take :: Word64 -> List a -> List a
take n xs =
    if n == 0
        then Nil
        else case xs of
            Nil -> Nil
            Cons x xs -> Cons x (take (n - 1) xs)

init :: List a -> List a
init xs =
    let Cons x rest = xs
     in case rest of
            Nil -> Nil
            Cons _ _ -> Cons x (init rest)

tail :: List a -> List a
tail xs =
    let Cons _ rest = xs
     in rest

last :: List a -> a
last xs =
    let Cons x rest = xs
     in case rest of
            Nil -> x
            Cons _ _ -> last rest

null :: List a -> Bool
null xs =
    case xs of Nil -> True; Cons _ _ -> False

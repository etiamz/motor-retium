-- Low sharing, no parallelisme; mirrors `peano-exponentiation.rete`.

-- \$ fourmolu --mode inplace nofib/imaginary/peano-exponentiation.hs

import Data.Word (Word64)
import System.Environment (getArgs)

data Nat = Z | S Nat

defaultInput :: Word64
defaultInput = 8

main :: IO ()
main = do
    args <- getArgs
    let n = case args of
            [] -> defaultInput
            (input : _) -> read input
    print (show (int (pow (nat 3) (nat n))))

nat :: Word64 -> Nat
nat n =
    if n < 1 then Z else S (nat (n - 1))

int :: Nat -> Word64
int x =
    case x of Z -> 0; S x -> 1 + int x

add :: Nat -> Nat -> Nat
add x y =
    case x of Z -> y; S x -> S (add x y)

mul :: Nat -> Nat -> Nat
mul x y =
    case y of Z -> Z; S y -> add (mul x y) x

pow :: Nat -> Nat -> Nat
pow x y =
    case y of Z -> S Z; S y -> mul x (pow x y)

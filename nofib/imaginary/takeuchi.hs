-- No sharing, maximal parallelisme; mirrors `takeuchi.rete`.

-- \$ fourmolu --mode inplace nofib/imaginary/takeuchi.hs

import Data.Int (Int64)
import System.Environment (getArgs)

defaultX :: Int64
defaultX = 35

defaultY :: Int64
defaultY = 18

defaultZ :: Int64
defaultZ = 9

main :: IO ()
main = do
    args <- getArgs
    let (x, y, z) = case args of
            (a : b : c : _) -> (read a, read b, read c)
            _ -> (defaultX, defaultY, defaultZ)
    print (show (tak x y z))

tak :: Int64 -> Int64 -> Int64 -> Int64
tak x y z =
    if not (y < x)
        then z
        else tak (tak (x - 1) y z) (tak (y - 1) z x) (tak (z - 1) x y)

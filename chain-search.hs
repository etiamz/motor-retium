{-# LANGUAGE MultiWayIf #-}

-- The sequential Haskell baseline for `chain-search.rete`.

-- \$ fourmolu --mode inplace chain-search.hs

module Main (main) where

import Control.Exception (Exception, catch, throw)
import Data.Bits (countTrailingZeros, shiftL, shiftR, (.|.))
import qualified Data.ByteString as BS
import Data.Int (Int64)
import Data.Word (Word64)
import System.Environment (getArgs)
import System.Exit (exitFailure)
import System.IO (hPutStrLn, stderr)

data List a = Nil | Cons a (List a)

data State = State (List Word64) Word64 BS.ByteString BS.ByteString

data Exhausted = Exhausted

defaultTarget :: Word64
defaultTarget = 100000003

defaultLevel :: Word64
defaultLevel = 29

main :: IO ()
main =
    run `catch` \(Panic message) -> do
        hPutStrLn stderr ("Panic: User panic: " ++ show message)
        exitFailure

run :: IO ()
run =
    do
        args <- getArgs
        let (target, level) = case args of
                (a : b : _) -> (read a, read b)
                _ -> (defaultTarget, defaultLevel)
        case solve target level of
            Exhausted -> print "No solution"

render :: List Word64 -> String
render elems =
    case elems of
        Nil -> panic "Empty addition chain impossible"
        Cons x xs -> case xs of
            Nil -> show x
            Cons _ _ -> renderAux xs ++ show x

renderAux :: List Word64 -> String
renderAux elems =
    case elems of
        Nil -> ""
        Cons x xs -> renderAux xs ++ show x ++ " "

solve :: Word64 -> Word64 -> Exhausted
solve target level =
    let st = State (Cons 1 Nil) level (bounds vertical) (bounds slant)
     in search st
  where
    search st =
        let State elems nremaining verticalBounds slantBounds = st
         in let Cons top rest = elems
             in if
                    | top == target -> panic (render elems)
                    | top < read64 verticalBounds nremaining -> Exhausted
                    | otherwise -> case rest of
                        Cons penultimate _
                            | top + penultimate < read64 slantBounds nremaining
                                && top * shiftL 1 (fromIntegral nremaining) /= target ->
                                Exhausted
                        Cons _ _ -> expand st
                        Nil -> expand st

    expand st =
        let State elems nremaining _ _ = st
         in let Cons top _ = elems
             in if nremaining <= 4 then stars st top elems else pairs st top elems

    pairs st top elems =
        case elems of
            Nil -> Exhausted
            Cons x xs -> sums st top x elems xs

    sums st top x elems pending =
        case elems of
            Nil -> pairs st top pending
            Cons y ys | x + y <= top -> sums st top x ys pending
            Cons y ys | x + y > target -> sums st top x ys pending
            Cons y ys -> join (proceed st (x + y)) (sums st top x ys pending)

    stars st top elems =
        case elems of
            Nil -> Exhausted
            Cons y ys | top + y > target -> stars st top ys
            Cons y ys -> join (proceed st (top + y)) (stars st top ys)

    join a b =
        case a of Exhausted -> b

    proceed st candidate =
        let State elems nremaining verticalBounds slantBounds = st
         in let st' =
                    State
                        (Cons candidate elems)
                        (nremaining - 1)
                        verticalBounds
                        slantBounds
             in search st'

    bounds sequence =
        goBounds sequence 0

    goBounds sequence i =
        if i > level
            then BS.empty
            else prepend64 (goBounds sequence (i + 1)) (sequence i)

    vertical nremaining =
        let n = target
         in let lb = fromIntegral level :: Int64
             in let i = lb - fromIntegral nremaining
                 in let t = fromIntegral (countTrailingZeros n)
                     in if
                            | lb - t - 1 <= i && i <= lb ->
                                ceildiv n (pow2 (lb - i))
                            | i == lb - t - 2 ->
                                ceildiv n (pow2 (t) * (pow2 (lb - t - (i + 1)) + 1))
                            | 0 <= i
                                && i <= lb - t - 3
                                && n `mod` (pow2 (lb - t - i - 2) + 1) /= 0 ->
                                ceildiv n (pow2 (t) * (pow2 (lb - t - (i + 1)) + 1))
                            | 0 <= i
                                && i <= lb - t - 3
                                && n `mod` (pow2 (lb - t - i - 2) + 1) == 0 ->
                                ceildiv n (pow2 (t + 1) * (pow2 (lb - t - (i + 2)) + 1))
                            | otherwise -> panic "Unreachable in `vertical`"

    slant nremaining =
        let n = target
         in let lb = fromIntegral level :: Int64
             in let i = lb - fromIntegral nremaining + 1
                 in let t = fromIntegral (countTrailingZeros n)
                     in if
                            | nremaining == 0 ->
                                666
                            | lb - t <= i && i <= lb ->
                                ceildiv n (pow2 (lb - i))
                            | 0 <= i
                                && i <= lb - t - 1
                                && n `mod` (pow2 (lb - t - i) + 1) /= 0 ->
                                ceildiv (3 * n) (pow2 (t) * (pow2 (lb - t - i + 1) + 1))
                            | 0 <= i
                                && i <= lb - t - 1
                                && n `mod` (pow2 (lb - t - i) + 1) == 0 ->
                                ceildiv (3 * n) (pow2 (t + 1) * (pow2 (lb - t - i) + 1))
                            | otherwise -> panic "Unreachable in `slant`"

prepend64 :: BS.ByteString -> Word64 -> BS.ByteString
prepend64 memo candidate =
    packedWord64 candidate <> memo

read64 :: BS.ByteString -> Word64 -> Word64
read64 bytes i =
    let offset = fromIntegral i * 8
     in let byte j = fromIntegral (BS.index bytes (offset + j))
         in byte 0
                .|. shiftL (byte 1) 8
                .|. shiftL (byte 2) 16
                .|. shiftL (byte 3) 24
                .|. shiftL (byte 4) 32
                .|. shiftL (byte 5) 40
                .|. shiftL (byte 6) 48
                .|. shiftL (byte 7) 56

packedWord64 :: Word64 -> BS.ByteString
packedWord64 value =
    BS.pack
        [ fromIntegral value
        , fromIntegral (shiftR value 8)
        , fromIntegral (shiftR value 16)
        , fromIntegral (shiftR value 24)
        , fromIntegral (shiftR value 32)
        , fromIntegral (shiftR value 40)
        , fromIntegral (shiftR value 48)
        , fromIntegral (shiftR value 56)
        ]

{-# INLINE ceildiv #-}
ceildiv :: Word64 -> Word64 -> Word64
ceildiv a b =
    (a - 1) `div` b + 1

{-# INLINE pow2 #-}
pow2 :: Int64 -> Word64
pow2 e =
    shiftL 1 (fromIntegral e)

newtype Panic = Panic String

instance Show Panic where
    show (Panic message) =
        message

instance Exception Panic

panic :: String -> a
panic message =
    throw (Panic message)

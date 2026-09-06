{-# LANGUAGE MultiWayIf #-}

-- The sequential Haskell baseline for `chain-search.rete`.

-- \$ fourmolu --mode inplace chain-search.hs

module Main (main) where

import Control.Exception (Exception, catch, throw)
import Data.Bits (shiftR)
import Data.Word (Word64)
import System.Environment (getArgs)
import System.Exit (exitFailure)
import System.IO (hPutStrLn, stderr)

data List a = Nil | Cons a (List a)

data State = State (List Word64) Word64

data Exhausted = Exhausted

defaultTarget :: Word64
defaultTarget = 80000003

defaultLevel :: Word64
defaultLevel = 28

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

newtype Panic = Panic String

instance Show Panic where
    show (Panic message) =
        message

instance Exception Panic

panic :: String -> a
panic message =
    throw (Panic message)

solve :: Word64 -> Word64 -> Exhausted
solve target level =
    search (State (Cons 1 Nil) level)
  where
    search st =
        let State elems nremaining = st
         in let Cons top _ = elems
             in if
                    | top == target -> panic (render elems)
                    | nremaining < 64
                        && top
                            <= shiftR (target - 1) (fromIntegral nremaining) ->
                        Exhausted
                    | nremaining <= 4 -> stars st top elems
                    | otherwise -> pairs st top elems Nil

    pairs st top elems memo =
        case elems of
            Nil -> Exhausted
            Cons x _ | x + x <= top -> Exhausted
            Cons x xs -> sums st top x elems xs memo

    sums st top x elems pending memo =
        case elems of
            Nil -> pairs st top pending memo
            Cons y ys | let candidate = x + y ->
                case () of
                    _
                        | candidate <= top ->
                            pairs st top pending memo
                    _
                        | candidate > target || contains candidate memo ->
                            sums st top x ys pending memo
                    _
                        | Exhausted <- proceed st candidate ->
                            sums st top x ys pending (Cons candidate memo)

    stars st top elems =
        case elems of
            Nil -> Exhausted
            Cons y ys
                | let candidate = top + y ->
                    case () of
                        _
                            | candidate <= top ->
                                Exhausted
                        _
                            | candidate > target ->
                                stars st top ys
                        _
                            | Exhausted <- proceed st candidate ->
                                stars st top ys

    proceed st candidate =
        let State elems nremaining = st
         in search (State (Cons candidate elems) (nremaining - 1))

    contains candidate memo =
        case memo of
            Nil -> False
            Cons x xs -> candidate == x || contains candidate xs

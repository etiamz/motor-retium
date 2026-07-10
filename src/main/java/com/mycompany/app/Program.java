package com.mycompany.app;

import java.util.Map;

public record Program(Term main, Map<String, Term> definitions) {
}

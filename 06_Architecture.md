# Architecture

## Objective

Keep architecture simple.

Focus on logging.

Do not optimize for future features.

---

## Components

Frontend

React

↓

Backend

Spring Boot

↓

AI Parser

OpenAI API

↓

Database

PostgreSQL

---

## Flow

User enters text

↓

Frontend sends request

↓

Backend calls OpenAI

↓

OpenAI returns structured event

↓

Backend stores event

↓

Timeline updates

---

## Design Principles

Simple

Maintainable

Low cost

Fast implementation

Minimal dependencies
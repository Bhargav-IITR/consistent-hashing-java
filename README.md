# Consistent Hashing in Java

A simple implementation of Consistent Hashing with virtual nodes in Java.

## Features

- Hash ring using `TreeMap`
- Virtual nodes (replicas)
- Dynamic server addition/removal
- Minimal key redistribution
- Automatic key reassignment on node failure

## Concepts Covered

- Distributed Systems
- Load Balancing
- Horizontal Scaling
- Fault Tolerance
- Hash Rings
- Data Partitioning

## Tech Stack

- Java
- MD5 Hashing
- TreeMap

## Example

```bash
UserA -> S1
UserB -> S3

Adding S6...

Removing S2...

# Reactive microservices architecture for Akka Cluster
Microservices framework is extension of Akka tool for developing distributed systems and applications. It provides a basic 
skeleton for building asynchronny services so called microservices. It is built on actor system and provides three main function 
for programmers which develop microservices applications:

* Register
* Lookup
* Terminate

It also provide system of dependecies which is responsible for informing a microservices about state of all others microservices.

# Abstract
A Microservice architecture gradually replaces a monolithic architecture in designing enterprise applications because with increasing requirements, the system becomes too complex, difficult and inconvenient to all customer’s requirements. The Microservices tries to spread complexity of large systems over several smaller services. The goal of thesis is to create tool for the development of microservices. The tool facilitate development by offering an automated management of microservice lifecycle and implemented communication between services to developers. The tool is built as an Akka extension and its part Akka Cluster. It is modeled on the actors who provide a good solution for building a reactive distributed application. An important part of this work is deep analysis of software architectures and processes to create enterprise systems. The analysis includes assessment of other tools which can create service–oriented architecture. The provided framework for building microservices attempts to solve the disadvantages of the analyzed platforms.


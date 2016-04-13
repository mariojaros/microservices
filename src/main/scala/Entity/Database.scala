package entity

import sorm._

/**
 * Created by mariojaros on 05.04.16.
 */
case class Employer(
                     publicId: String,
                     name: String,
                     surname: String,
                     address: String,
                     age: Int
                     )


object Db extends Instance(
  entities = Set(
    Entity[Employer]()),
  url = "jdbc:h2:mem:test",
  user = "",
  password = "",
  initMode = InitMode.Create
)

case object InitDatabase {

  def init() = {
    Db.save(Employer("1", "Mario", "Jaros", "Kvetna", 23))
    Db.save(Employer("2", "Peter", "Chovanec", "Ulica", 26))
    Db.save(Employer("3", "Pavol", "Jaros", "Kvetna", 19))
  }
}


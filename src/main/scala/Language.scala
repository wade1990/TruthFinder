object Language {

  trait State
  trait WorldState[W <: World[W]] extends State
  trait Race extends State {
    def personality(truth: Truth[_]): Boolean => Boolean //Sometimes personality might depend on states of the truth
  }

  sealed trait Reference[+S <: State]
  case class Name(charName: String) extends Reference[Race]
  trait WorldAspectReference[W <: World[W], +WS <: WorldState[W]] extends Reference[WS]
  trait CollectiveReference extends Reference[Race]
  case object Everyone extends CollectiveReference
  ///NumberOfPeople needs but me identifier
  case class NumberOfPeople(number:Int, isExact:Boolean) extends CollectiveReference

  trait TruthPiece[+S <: State] {
    def reference: Reference[S]
    def state: Option[S]

    def merge(other:TruthPiece[_]):TruthPiece[S]
  }
  case class Character(reference: Name, state: Option[Race]) extends TruthPiece[Race] {
    def merge(other:TruthPiece[_]):TruthPiece[Race] =
      Character(reference,
        (state, other.state) match {
          case (Some(r1), Some(r2)) if r1 == r2 =>
            state
          case _ =>
            None
        })
  }

  case class WorldAspect[W <:World[W], WS <: WorldState[W]](reference: WorldAspectReference[W, WS], state: Option[WS]) extends TruthPiece[WS]{
    def merge(other:TruthPiece[_]):TruthPiece[WS] =
      WorldAspect(reference,
        (state, other.state) match {
          case (Some(r1), Some(r2)) if r1 == r2 =>
            state
          case _ =>
            None
        })
  }

  case class Sentence[W <: World[W]](speaker:Name, subject:Reference[State], directObject:State, affirmative: Boolean = true) {
    ///Still does not handles cases correctly
    ///I am sure one of these two sentences are wrongly evaluated:
    // Everyone is Ogre
    // Everyone is (Something else than) Ogre === NoOne is Ogre
    // Check NoOne, NoOneButMe, SomeOne, SomeOneButMe
    def compareWithTruth(truth: Truth[W]): Option[Boolean] =
      subject match {
        case Everyone =>
          val everyone = truth.truthPieces.collect({case ch:Character  => compareWithTruthPiece(ch)})
          everyone.find(_.contains(false)) match {//looking for contradictions
            case Some(_) =>
              Some(false)//case where we could find a contradiction
            case None => //looking for places where we are not sure
              everyone.find(_.isEmpty) match {
                case Some(_) =>
                  None // case where we have not contradiction but we could find some place where we are not sure
                case None =>
                  Some(true) //case where we are sure that the sentence is true
              }
          }
        //Optimization for obvious cases (3 people and says: There are 4 Ogres)
        case NumberOfPeople(number, isExact) =>
          val everyone = truth.truthPieces.collect({case ch:Character => compareWithTruthPiece(ch)})
          val matches = everyone.count(_.contains(true))
          val unknowns = everyone.count(_.isEmpty)
          isExact match {
            case true if matches == number && unknowns == 0 =>
              Some(true) //seeking for exact number of people and we are sure that is the case
            case true if matches < number && matches + unknowns >= number =>
              None //seeking for exact number of people and we are not sure if that is the case
            case false if matches >= number =>
              Some(true) //seeking for not exact number of people and we are sure that is the case
            case false if matches + unknowns >= number =>
              None //seeking for not exact number of people and we are sure if that is the case
            case _ =>
              Some(false) //otherwise is a contradiction
          }

        case _ =>
          truth.truthPieces.find(_.reference == subject).flatMap(compareWithTruthPiece)
      }

    private def compareWithTruthPiece(tp:TruthPiece[State]):Option[Boolean] =
      tp.reference match {
        case tpRef if tpRef == subject => //In case we got the sentence topic, we compare with DO
          tp.state.map(compareStateAndDO)
        case _ => //In case we got a TruthPiece the sentence doesn't talk about, we return None
          None
      }

    private def compareStateAndDO(s: State): Boolean =
      if (affirmative) s == directObject
      else s != directObject
  }

}
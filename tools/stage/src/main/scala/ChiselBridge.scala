package chipyard.stage

import chisel3.RawModule
import chisel3.stage._
import chisel3.stage.phases._
import chisel3.experimental.EnumAnnotations.{EnumComponentAnnotation, EnumDefAnnotation, EnumVecAnnotation}
import chisel3.util.experimental.decode.DecodeTableAnnotation
import firrtl.passes.InlineAnnotation
import firrtl.transforms.FlattenAnnotation
// this imports the [[firrtl]] package from Chisel (not to be confused with the firrtl2 compiler!
import firrtl._
import firrtl.ir._
import firrtl.annotations._
import firrtl.stage.FirrtlCircuitAnnotation
import firrtl.passes.wiring.{SinkAnnotation, SourceAnnotation}
import firrtl.transforms.{DontTouchAnnotation, NoDedupAnnotation}
import logger.LogLevelAnnotation

/// Indicates that an unsupported Chisel annotation was encountered
case class UnsupportedAnnotation(name: String, instance: String) extends firrtl2.annotations.NoTargetAnnotation

/// This annotation can be used to pass firrtl2 annotations through Chisel
private[chipyard] case class Firrtl2AnnotationWrapper(anno: firrtl2.annotations.Annotation) extends NoTargetAnnotation
private[chipyard] object convertTargetToFirrtl2 {
  def apply(r: ReferenceTarget): firrtl2.annotations.ReferenceTarget =
    firrtl2.annotations.ReferenceTarget(
      r.circuit,
      r.module,
      r.path.map { case (instance, module) => (convertInst(instance), convertOfMod(module)) },
      r.ref,
      r.component.map(convertToken)
    )
  private def convertInst(i: TargetToken.Instance): firrtl2.annotations.TargetToken.Instance =
    firrtl2.annotations.TargetToken.Instance(i.value)
  private def convertOfMod(o: TargetToken.OfModule): firrtl2.annotations.TargetToken.OfModule =
    firrtl2.annotations.TargetToken.OfModule(o.value)
  private def convertToken(t: TargetToken): firrtl2.annotations.TargetToken = t match {
    case i: TargetToken.Instance => convertInst(i)
    case o: TargetToken.OfModule => convertOfMod(o)
    case TargetToken.Ref(value)   => firrtl2.annotations.TargetToken.Ref(value)
    case TargetToken.Index(value) => firrtl2.annotations.TargetToken.Index(value)
    case TargetToken.Field(value) => firrtl2.annotations.TargetToken.Field(value)
    case TargetToken.Clock        => firrtl2.annotations.TargetToken.Clock
    case TargetToken.Init         => firrtl2.annotations.TargetToken.Init
    case TargetToken.Reset        => firrtl2.annotations.TargetToken.Reset
  }
}

/// wraps Chisel elaboration to bridge it over into the firrtl2 world
object ChiselBridge {
  private val elaboratePhase = new Elaborate
  private val maybeAspects = new MaybeAspectPhase
  private val converter = new Convert

  def elaborate[M <: RawModule](
    gen:         () => M,
    userAnnos:   firrtl2.AnnotationSeq,
    chiselAnnos: firrtl.AnnotationSeq
  ): (firrtl2.CircuitState, M) = {
    // run Builder.build(Module(gen()))
    val genAnno = ChiselGeneratorAnnotation(gen)
    val elaborationAnnos: firrtl.AnnotationSeq = elaboratePhase.transform(genAnno +: chiselAnnos)

    // extract elaborated module
    val dut: M = elaborationAnnos.collectFirst { case DesignAnnotation(d) => d }.get.asInstanceOf[M]

    // run aspects
    val aspectAnnos: firrtl.AnnotationSeq = maybeAspects.transform(elaborationAnnos)

    // run Converter.convert(a.circuit) and toFirrtl on all annotations
    val converterAnnos: firrtl.AnnotationSeq = converter.transform(aspectAnnos)

    // annos to state
    val state = annosToState(converterAnnos)
    val stateWithUserAnnos = state.copy(annotations = state.annotations ++ userAnnos)

    (stateWithUserAnnos, dut)
  }

  private val RunWiring = firrtl2.stage.RunFirrtlTransformAnnotation(
    firrtl2.options.Dependency[firrtl2.passes.wiring.WiringTransform]
  )
  private def addPassesBasedOnAnnos(annos: firrtl2.AnnotationSeq): firrtl2.AnnotationSeq = {
    // the boring utils Chisel library used to automatically schedule the Wiring transform
    // we now have to do this manually
    val usesBoring = annos.exists(_.isInstanceOf[firrtl2.passes.wiring.SinkAnnotation])
    val runWiring = if (usesBoring) Seq(RunWiring) else Seq()
    runWiring ++: annos
  }

  def annosToState(annos: AnnotationSeq): firrtl2.CircuitState = {
    val circuit = annos.collectFirst { case FirrtlCircuitAnnotation(c) => c }.get
    val filteredAnnos = annos.filterNot(isInternalAnno)
    val firrtl2Annos = filteredAnnos.flatMap(convert)
    val firrtl2Circuit = convert(circuit)
    firrtl2.CircuitState(firrtl2Circuit, addPassesBasedOnAnnos(firrtl2Annos))
  }

  private def isInternalAnno(a: Annotation): Boolean = a match {
    case _: FirrtlCircuitAnnotation | _: DesignAnnotation[_] | _: ChiselCircuitAnnotation |
        _: EmittedCircuitAnnotation[_] | _: LogLevelAnnotation =>
      true
    case _ => false
  }

  private def convert(m: ModuleTarget): firrtl2.annotations.ModuleTarget =
    firrtl2.annotations.ModuleTarget(m.circuit, m.module)
  private def convert(c: ComponentName): firrtl2.annotations.ComponentName =
    firrtl2.annotations.ComponentName(c.name, convert(c.module))
  private def convert(c: CircuitName): firrtl2.annotations.CircuitName = firrtl2.annotations.CircuitName(c.name)
  private def convertNamed(n: Named): firrtl2.annotations.Named = n match {
    case target: Target      => ???
    case c:      CircuitName => convert(c)
    case ModuleName(name, circuit) => firrtl2.annotations.ModuleName(name, convert(circuit))
    case c: ComponentName => convert(c)
  }
  private def convert(hexOrBinary: MemoryLoadFileType): firrtl2.annotations.MemoryLoadFileType = hexOrBinary match {
    case MemoryLoadFileType.Hex    => firrtl2.annotations.MemoryLoadFileType.Hex
    case MemoryLoadFileType.Binary => firrtl2.annotations.MemoryLoadFileType.Binary
  }
  private def convert(anno: Annotation): Option[firrtl2.annotations.Annotation] = anno match {
    // ignore any Chisel options
    case _: ChiselOption => None
    // our special annotation to allow the injection of "firrtl2" annotations
    case Firrtl2AnnotationWrapper(anno) => Some(anno)
    // black box related annotations (from ExtModule)
    case firrtl.transforms.BlackBoxInlineAnno(target, name, text) =>
      Some(firrtl2.transforms.BlackBoxInlineAnno(convert(target), name, text))
    case firrtl.transforms.BlackBoxPathAnno(target, path) =>
      Some(firrtl2.transforms.BlackBoxPathAnno(convert(target), path))
    case firrtl.transforms.BlackBoxTargetDirAnno(targetDir) =>
      Some(firrtl2.transforms.BlackBoxTargetDirAnno(targetDir))
    case firrtl.transforms.BlackBoxResourceFileNameAnno(resourceFileName) =>
      Some(firrtl2.transforms.BlackBoxResourceFileNameAnno(resourceFileName))
    // wiring annos (from Boring Utils)
    case SourceAnnotation(target, pin) => Some(firrtl2.passes.wiring.SourceAnnotation(convert(target), pin))
    case SinkAnnotation(target, pin)   => Some(firrtl2.passes.wiring.SinkAnnotation(convertNamed(target), pin))
    // Inline.scala
    case InlineAnnotation(target)  => Some(firrtl2.passes.InlineAnnotation(convertNamed(target)))
    case FlattenAnnotation(target) => Some(firrtl2.transforms.FlattenAnnotation(convertNamed(target)))
    case NoDedupAnnotation(target) => Some(firrtl2.transforms.NoDedupAnnotation(convert(target)))
    // don't touch
    case DontTouchAnnotation(target) => Some(firrtl2.transforms.DontTouchAnnotation(convert(target)))
    // memory annotations
    case MemoryFileInlineAnnotation(target, filename, hexOrBinary) =>
      Some(firrtl2.annotations.MemoryFileInlineAnnotation(convert(target), filename, convert(hexOrBinary)))
    // enum annotations
    case EnumComponentAnnotation(target, enumTypeName) =>
      Some(firrtl2.annotations.EnumComponentAnnotation(convertNamed(target), enumTypeName))
    case EnumDefAnnotation(typeName, definition) =>
      Some(firrtl2.annotations.EnumDefAnnotation(typeName, definition))
    case EnumVecAnnotation(target, typeName, fields) =>
      Some(firrtl2.annotations.EnumVecAnnotation(convertNamed(target), typeName, fields))
    // ignoreDecodeTableAnnotation since it is not needed by the firrtl compiler
    case _: DecodeTableAnnotation => None
    //
    case _ =>
      println(
        s"[WARNING] Unsupported annotation: ${anno.getClass.getSimpleName}\n" +
          s" Please report this issue at https://github.com/ucb-bar/chipyard/issues"
      )
      Some(UnsupportedAnnotation(anno.getClass.getSimpleName, anno.toString))
  }

  private def convert(c: Circuit): firrtl2.ir.Circuit =
    firrtl2.ir.Circuit(convert(c.info), c.modules.map(convert), c.main)
  private def convert(i: Info): firrtl2.ir.Info = i match {
    case FileInfo(escaped) => firrtl2.ir.FileInfo(escaped)
    case NoInfo            => firrtl2.ir.NoInfo
  }
  private def convert(m: DefModule): firrtl2.ir.DefModule = m match {
    case ExtModule(info, name, ports, defname, params) =>
      firrtl2.ir.ExtModule(convert(info), name, ports.map(convert), defname, params.map(convert))
    case IntModule(info, name, ports, intrinsic, params) =>
      // TODO: add proper intrinsic module support
      firrtl2.ir.ExtModule(convert(info), name, ports.map(convert), intrinsic, params.map(convert))
    case Module(info, name, ports, body) =>
      firrtl2.ir.Module(convert(info), name, ports.map(convert), convert(body))

  }
  private def convert(s: StringLit): firrtl2.ir.StringLit = firrtl2.ir.StringLit(s.string)
  private def convert(p: Param): firrtl2.ir.Param = p match {
    case IntParam(name, value)       => firrtl2.ir.IntParam(name, value)
    case DoubleParam(name, value)    => firrtl2.ir.DoubleParam(name, value)
    case StringParam(name, value)    => firrtl2.ir.StringParam(name, convert(value))
    case RawStringParam(name, value) => firrtl2.ir.RawStringParam(name, value)
  }
  private def convert(d: Direction): firrtl2.ir.Direction = d match {
    case Input  => firrtl2.ir.Input
    case Output => firrtl2.ir.Output
  }
  private def convert(o: Orientation): firrtl2.ir.Orientation = o match {
    case Default => firrtl2.ir.Default
    case Flip    => firrtl2.ir.Flip
  }
  private def convert(w: Width): firrtl2.ir.Width = w match {
    case IntWidth(value) => firrtl2.ir.IntWidth(value)
    case UnknownWidth    => firrtl2.ir.UnknownWidth
  }
  private def convert(t: Type): firrtl2.ir.Type = t match {
    case UIntType(w)    => firrtl2.ir.UIntType(convert(w))
    case SIntType(w)    => firrtl2.ir.SIntType(convert(w))
    case ClockType      => firrtl2.ir.ClockType
    case ResetType      => firrtl2.ir.ResetType
    case AsyncResetType => firrtl2.ir.AsyncResetType
    case AnalogType(w)  => firrtl2.ir.AnalogType(convert(w))
    case UnknownType    => firrtl2.ir.UnknownType
    case ConstType(tpe) => convert(tpe)
    case BundleType(fields) =>
      firrtl2.ir.BundleType(fields.map { case Field(name, flip, tpe) =>
        firrtl2.ir.Field(name, convert(flip), convert(tpe))
      })
    case VectorType(tpe, size) => firrtl2.ir.VectorType(convert(tpe), size)
  }
  private def convert(p: Port): firrtl2.ir.Port =
    firrtl2.ir.Port(convert(p.info), p.name, convert(p.direction), convert(p.tpe))
  private def convert(op: Formal.Value): firrtl2.ir.Formal.Value = op match {
    case firrtl.ir.Formal.Assert => firrtl2.ir.Formal.Assert
    case firrtl.ir.Formal.Assume => firrtl2.ir.Formal.Assume
    case firrtl.ir.Formal.Cover  => firrtl2.ir.Formal.Cover
  }
  private def convert(d: MPortDir): firrtl2.MPortDir = d match {
    case MInfer     => firrtl2.MInfer
    case MRead      => firrtl2.MRead
    case MReadWrite => firrtl2.MReadWrite
    case MWrite     => firrtl2.MWrite
  }
  private def convertReadUnderWrite(r: ReadUnderWrite.Value): firrtl2.ir.ReadUnderWrite.Value = r match {
    case firrtl.ir.ReadUnderWrite.Undefined => firrtl2.ir.ReadUnderWrite.Undefined
    case firrtl.ir.ReadUnderWrite.Old       => firrtl2.ir.ReadUnderWrite.Old
    case firrtl.ir.ReadUnderWrite.New       => firrtl2.ir.ReadUnderWrite.New
  }
  private def convert(s: Statement): firrtl2.ir.Statement = s match {
    case DefNode(info, name, value) => firrtl2.ir.DefNode(convert(info), name, convert(value))
    case Connect(info, loc, expr)   => firrtl2.ir.Connect(convert(info), convert(loc), convert(expr))
    case Conditionally(info, pred, conseq, alt) =>
      firrtl2.ir.Conditionally(convert(info), convert(pred), convert(conseq), convert(alt))
    case EmptyStmt                           => firrtl2.ir.EmptyStmt
    case Block(stmts)                        => firrtl2.ir.Block(stmts.map(convert))
    case DefWire(info, name, tpe)            => firrtl2.ir.DefWire(convert(info), name, convert(tpe))
    case DefRegister(info, name, tpe, clock) =>
      // default is a self reset with a zero reset signal
      val reset = firrtl2.Utils.zero
      val firrtl2Tpe = convert(tpe)
      val init = firrtl2.ir.Reference(name, firrtl2Tpe, firrtl2.RegKind, firrtl2.SourceFlow)
      firrtl2.ir.DefRegister(convert(info), name, firrtl2Tpe, convert(clock), reset, init)
    case DefRegisterWithReset(info, name, tpe, clock, reset, init) =>
      firrtl2.ir.DefRegister(convert(info), name, convert(tpe), convert(clock), convert(reset), convert(init))
    case DefInstance(info, name, module, tpe) => firrtl2.ir.DefInstance(convert(info), name, module, convert(tpe))
    case Attach(info, exprs)                  => firrtl2.ir.Attach(convert(info), exprs.map(convert))
    case s: Stop => firrtl2.ir.Stop(convert(s.info), s.ret, convert(s.clk), convert(s.en), name = s.name)
    case p: Print =>
      firrtl2.ir.Print(
        convert(p.info),
        convert(p.string),
        p.args.map(convert),
        convert(p.clk),
        convert(p.en),
        name = p.name
      )
    case v: Verification =>
      firrtl2.ir.Verification(
        convert(v.op),
        convert(v.info),
        convert(v.clk),
        convert(v.pred),
        convert(v.en),
        convert(v.msg),
        name = v.name
      )
    case IsInvalid(info, expr) => firrtl2.ir.IsInvalid(convert(info), convert(expr))
    case CDefMemory(info, name, tpe, size, seq, readUnderWrite) =>
      firrtl2.CDefMemory(convert(info), name, convert(tpe), size, seq, convertReadUnderWrite(readUnderWrite))
    case CDefMPort(info, name, tpe, mem, exps, direction) =>
      firrtl2.CDefMPort(convert(info), name, convert(tpe), mem, exps.map(convert), convert(direction))
    case other => throw new NotImplementedError(s"TODO: convert ${other}")
  }
  private def convert(op: PrimOp): firrtl2.ir.PrimOp = op match {
    case PrimOps.Add          => firrtl2.PrimOps.Add
    case PrimOps.Sub          => firrtl2.PrimOps.Sub
    case PrimOps.Mul          => firrtl2.PrimOps.Mul
    case PrimOps.Div          => firrtl2.PrimOps.Div
    case PrimOps.Rem          => firrtl2.PrimOps.Rem
    case PrimOps.Lt           => firrtl2.PrimOps.Lt
    case PrimOps.Leq          => firrtl2.PrimOps.Leq
    case PrimOps.Gt           => firrtl2.PrimOps.Gt
    case PrimOps.Geq          => firrtl2.PrimOps.Geq
    case PrimOps.Eq           => firrtl2.PrimOps.Eq
    case PrimOps.Neq          => firrtl2.PrimOps.Neq
    case PrimOps.Pad          => firrtl2.PrimOps.Pad
    case PrimOps.AsUInt       => firrtl2.PrimOps.AsUInt
    case PrimOps.AsSInt       => firrtl2.PrimOps.AsSInt
    case PrimOps.AsClock      => firrtl2.PrimOps.AsClock
    case PrimOps.AsAsyncReset => firrtl2.PrimOps.AsAsyncReset
    case PrimOps.Shl          => firrtl2.PrimOps.Shl
    case PrimOps.Shr          => firrtl2.PrimOps.Shr
    case PrimOps.Dshl         => firrtl2.PrimOps.Dshl
    case PrimOps.Dshr         => firrtl2.PrimOps.Dshr
    case PrimOps.Neg          => firrtl2.PrimOps.Neg
    case PrimOps.Cvt          => firrtl2.PrimOps.Cvt
    case PrimOps.Not          => firrtl2.PrimOps.Not
    case PrimOps.And          => firrtl2.PrimOps.And
    case PrimOps.Or           => firrtl2.PrimOps.Or
    case PrimOps.Xor          => firrtl2.PrimOps.Xor
    case PrimOps.Andr         => firrtl2.PrimOps.Andr
    case PrimOps.Orr          => firrtl2.PrimOps.Orr
    case PrimOps.Xorr         => firrtl2.PrimOps.Xorr
    case PrimOps.Cat          => firrtl2.PrimOps.Cat
    case PrimOps.Bits         => firrtl2.PrimOps.Bits
    case PrimOps.Head         => firrtl2.PrimOps.Head
    case PrimOps.Tail         => firrtl2.PrimOps.Tail
  }
  private def convert(e: Expression): firrtl2.ir.Expression = e match {
    case Reference(name, tpe)          => firrtl2.ir.Reference(name, convert(tpe))
    case UIntLiteral(value, width)     => firrtl2.ir.UIntLiteral(value, convert(width))
    case SIntLiteral(value, width)     => firrtl2.ir.SIntLiteral(value, convert(width))
    case SubField(expr, name, tpe)     => firrtl2.ir.SubField(convert(expr), name, convert(tpe))
    case SubAccess(expr, index, tpe)   => firrtl2.ir.SubAccess(convert(expr), convert(index), convert(tpe))
    case SubIndex(expr, value, tpe)    => firrtl2.ir.SubIndex(convert(expr), value, convert(tpe))
    case DoPrim(op, args, consts, tpe) => firrtl2.ir.DoPrim(convert(op), args.map(convert), consts, convert(tpe))
    case Mux(cond, tval, fval, tpe)    => firrtl2.ir.Mux(convert(cond), convert(tval), convert(fval), convert(tpe))
    case other                         => throw new NotImplementedError(s"TODO: convert ${other}")
  }
}

# Assembler

The Pravda assembler (PASM) is a text representation of Pravda VM bytecode.

Let's consider one simple program written in PASM:

```
/* My program */
jump @main
@ok:
push "good"
jump @end
@main:
push 2
mul
push 8
gt
jumpi @ok
push "bad"
@end:
```

It pops an item from the stack and multiplies it by 2. If it is less than 8 it pushes "good" to the stack, otherwise it pushes "bad".

`pasm` operations are easy to understand. There are several things that differ from low-level bytecode:

1. You can define labels: `@my_label:`.
2. Jump to defined labels `jump @my_label`. Jump with the condition `jumi @my_label` and jump to functions with preserved call-stack `call @my_label`.
3. Push the primitive to the stack:`push <primitive>`. Or put an item to the heap: `new <data>` (in this case reference to data will be pushed to the stack).
4. Write comments: `/* a comment */`.
5. Work with structs: `struct_mut <primitive>`, `struct_get <primitive>`. This will produce `STRUCT_MUT_STATIC` and `STRUCT_GET_STATIC` opcodes that take the key for the struct field from bytecode. You can write `struct_mut` or `struct_get` without `<primitive>` literal. In this case, `STRUCT_MUT` and `STRUCT_GET` opcodes are used and the key is taken from the stack.
6. Use the regular orphan [opcodes](opcodes.md).
7. Add meta information via `meta <meta>`, see [`<meta>` definition](meta.md) for details.

See also [string `data` encoding](data.md).

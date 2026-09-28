#ifndef LAYOUT_PROFILE_FIXTURE_H
#define LAYOUT_PROFILE_FIXTURE_H
struct ProfileBasic { char c; long value; void *pointer; };
struct __attribute__((packed)) ProfilePacked { char c; long long value; };
struct __attribute__((aligned(16))) ProfileAligned { char c; };
union ProfileUnion { long long integer; double floating; };
struct ProfileBits { unsigned a:3; unsigned b:5; unsigned c:11; };
struct ProfileArrays { int fixed[4]; double matrix[2][3]; };
struct ProfileRecursive { struct ProfileRecursive *next; int value; };
typedef int ProfileVector __attribute__((ext_vector_type(4)));
typedef struct ProfileBasic ProfileAlias;
enum ProfileEnum { PROFILE_ZERO, PROFILE_ONE };
#ifdef __OBJC__
__attribute__((objc_root_class))
@interface ProfileObject { @public int value; }
@end
@interface ProfileChild : ProfileObject { @public long childValue; }
@end
#endif
#endif

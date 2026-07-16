class UserProfile {
  final String name;
  final int age;
  final String bloodGroup;

  UserProfile({
    required this.name,
    required this.age,
    required this.bloodGroup,
  });

  Map<String, dynamic> toJson() {
    return {
      "name": name,
      "age": age,
      "bloodGroup": bloodGroup,
    };
  }

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      name: json["name"],
      age: json["age"],
      bloodGroup: json["bloodGroup"],
    );
  }
}